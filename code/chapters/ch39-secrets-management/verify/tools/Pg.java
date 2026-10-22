import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * A tiny PostgreSQL client for the measurements: protocol 3.0 over TCP, SCRAM-SHA-256 password authentication
 * (RFC 5802/7677, no channel binding), simple queries. Enough to log in as the users OpenBao creates and to see
 * exactly what PostgreSQL answers. Not a driver: no TLS, no extended protocol, no type conversion.
 */
final class Pg implements AutoCloseable {

    static final class PgException extends IOException {
        private static final long serialVersionUID = 1L;
        final String sqlState;

        PgException(String sqlState, String message) {
            super(sqlState + " " + message);
            this.sqlState = sqlState;
        }
    }

    private final Socket socket;
    private final DataInputStream in;
    private final OutputStream out;

    Pg(String host, int port, String user, String password, String database) throws IOException {
        socket = new Socket(host, port);
        socket.setTcpNoDelay(true);
        in = new DataInputStream(socket.getInputStream());
        out = socket.getOutputStream();
        try {
            startup(user, password, database);
        } catch (IOException e) {
            socket.close();
            throw e;
        }
    }

    private void startup(String user, String password, String database) throws IOException {
        var body = new java.io.ByteArrayOutputStream();
        writeInt(body, 196608);
        for (String s : new String[] {"user", user, "database", database}) {
            body.writeBytes(cstr(s));
        }
        body.write(0);
        byte[] b = body.toByteArray();
        var msg = new java.io.ByteArrayOutputStream();
        writeInt(msg, b.length + 4);
        msg.writeBytes(b);
        out.write(msg.toByteArray());
        out.flush();

        String clientNonce = Base64.getEncoder().encodeToString(new SecureRandom().generateSeed(18));
        String clientFirstBare = "n=,r=" + clientNonce;
        String serverFirst = null;
        byte[] saltedPassword = null;
        String authMessage = null;
        while (true) {
            char type = (char) in.readUnsignedByte();
            byte[] payload = new byte[in.readInt() - 4];
            in.readFully(payload);
            switch (type) {
                case 'R' -> {
                    int code = ((payload[0] & 0xff) << 24) | ((payload[1] & 0xff) << 16) | ((payload[2] & 0xff) << 8) | (payload[3] & 0xff);
                    if (code == 0) {
                        continue;                                           // AuthenticationOk
                    }
                    if (code == 10) {                                       // SASL: mechanisms
                        byte[] first = ("n,," + clientFirstBare).getBytes(StandardCharsets.UTF_8);
                        var m = new java.io.ByteArrayOutputStream();
                        m.writeBytes(cstr("SCRAM-SHA-256"));
                        writeInt(m, first.length);
                        m.writeBytes(first);
                        send('p', m.toByteArray());
                    } else if (code == 11) {                                // SASLContinue: server-first
                        serverFirst = new String(payload, 4, payload.length - 4, StandardCharsets.UTF_8);
                        Map<String, String> f = attributes(serverFirst);
                        if (!f.get("r").startsWith(clientNonce)) {
                            throw new IOException("server nonce does not extend ours");
                        }
                        byte[] salt = Base64.getDecoder().decode(f.get("s"));
                        int iterations = Integer.parseInt(f.get("i"));
                        saltedPassword = pbkdf2(password, salt, iterations);
                        String clientFinalNoProof = "c=biws,r=" + f.get("r");
                        authMessage = clientFirstBare + "," + serverFirst + "," + clientFinalNoProof;
                        byte[] clientKey = hmac(saltedPassword, "Client Key");
                        byte[] storedKey = sha256(clientKey);
                        byte[] signature = hmac(storedKey, authMessage);
                        byte[] proof = new byte[clientKey.length];
                        for (int i = 0; i < proof.length; i++) {
                            proof[i] = (byte) (clientKey[i] ^ signature[i]);
                        }
                        send('p', (clientFinalNoProof + ",p=" + Base64.getEncoder().encodeToString(proof))
                                .getBytes(StandardCharsets.UTF_8));
                    } else if (code == 12) {                                // SASLFinal: check the server too
                        Map<String, String> f = attributes(new String(payload, 4, payload.length - 4, StandardCharsets.UTF_8));
                        byte[] expected = hmac(hmac(saltedPassword, "Server Key"), authMessage);
                        if (!MessageDigest.isEqual(expected, Base64.getDecoder().decode(f.get("v")))) {
                            throw new IOException("server signature mismatch");
                        }
                    } else {
                        throw new IOException("unsupported authentication request " + code);
                    }
                }
                case 'E' -> throw error(payload);
                case 'Z' -> {
                    return;
                }
                default -> {
                    // ParameterStatus, BackendKeyData, notices
                }
            }
        }
    }

    /** Runs one statement; returns the rows of the last result as strings (null for SQL NULL). */
    List<List<String>> query(String sql) throws IOException {
        send('Q', cstr(sql));
        List<List<String>> rows = new ArrayList<>();
        PgException failure = null;
        while (true) {
            char type = (char) in.readUnsignedByte();
            byte[] payload = new byte[in.readInt() - 4];
            in.readFully(payload);
            switch (type) {
                case 'D' -> {
                    var d = new DataInputStream(new java.io.ByteArrayInputStream(payload));
                    int n = d.readUnsignedShort();
                    List<String> row = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) {
                        int len = d.readInt();
                        if (len < 0) {
                            row.add(null);
                        } else {
                            byte[] v = new byte[len];
                            d.readFully(v);
                            row.add(new String(v, StandardCharsets.UTF_8));
                        }
                    }
                    rows.add(row);
                }
                case 'T' -> rows.clear();
                case 'E' -> failure = error(payload);
                case 'Z' -> {
                    if (failure != null) {
                        throw failure;
                    }
                    return rows;
                }
                default -> {
                }
            }
        }
    }

    boolean isOpen() {
        try {
            query("select 1");
            return true;
        } catch (EOFException | java.net.SocketException e) {
            return false;
        } catch (IOException e) {
            return !(e.getMessage() != null && e.getMessage().startsWith("57P01"));
        }
    }

    private static PgException error(byte[] payload) {
        Map<Character, String> f = new HashMap<>();
        int start = 0;
        for (int i = 0; i < payload.length; i++) {
            if (payload[i] == 0) {
                if (i > start) {
                    f.put((char) payload[start], new String(payload, start + 1, i - start - 1, StandardCharsets.UTF_8));
                }
                start = i + 1;
            }
        }
        return new PgException(f.getOrDefault('C', "?"), f.getOrDefault('M', ""));
    }

    private void send(char type, byte[] body) throws IOException {
        var m = new java.io.ByteArrayOutputStream();
        m.write(type);
        writeInt(m, body.length + 4);
        m.writeBytes(body);
        out.write(m.toByteArray());
        out.flush();
    }

    private static Map<String, String> attributes(String s) {
        Map<String, String> m = new HashMap<>();
        for (String part : s.split(",")) {
            int eq = part.indexOf('=');
            m.put(part.substring(0, eq), part.substring(eq + 1));
        }
        return m;
    }

    private static byte[] pbkdf2(String password, byte[] salt, int iterations) throws IOException {
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(new PBEKeySpec(password.toCharArray(), salt, iterations, 256)).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IOException(e);
        }
    }

    private static byte[] hmac(byte[] key, String data) throws IOException {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IOException(e);
        }
    }

    private static byte[] sha256(byte[] data) throws IOException {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (GeneralSecurityException e) {
            throw new IOException(e);
        }
    }

    private static byte[] cstr(String s) {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        byte[] r = new byte[b.length + 1];
        System.arraycopy(b, 0, r, 0, b.length);
        return r;
    }

    private static void writeInt(java.io.ByteArrayOutputStream o, int v) {
        o.write(v >>> 24);
        o.write(v >>> 16);
        o.write(v >>> 8);
        o.write(v);
    }

    @Override
    public void close() {
        try {
            send('X', new byte[0]);
        } catch (IOException ignored) {
        }
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}
