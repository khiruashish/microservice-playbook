package dev.foodfleet.ch52.pg;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * The database health check, without a JDBC driver: connects to PostgreSQL, logs in (trust authentication only:
 * this is for the lab's database) and runs {@code SELECT 1} over the frontend/backend protocol, within a timeout.
 * In a Spring Boot service this is the {@code db} health contributor, backed by the connection pool.
 */
public final class PgPing {

    private final String host;
    private final int port;
    private final String user;
    private final String database;
    private final Duration timeout;

    public PgPing(String host, int port, String user, String database, Duration timeout) {
        this.host = host;
        this.port = port;
        this.user = user;
        this.database = database;
        this.timeout = timeout;
    }

    /** Runs SELECT 1; throws if the database can't be reached, refuses the login or doesn't answer in time. */
    public void ping() throws IOException {
        try (Socket s = new Socket()) {
            int t = (int) timeout.toMillis();
            s.connect(new InetSocketAddress(host, port), t);
            s.setSoTimeout(t);
            DataOutputStream out = new DataOutputStream(s.getOutputStream());
            DataInputStream in = new DataInputStream(s.getInputStream());
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            DataOutputStream b = new DataOutputStream(body);
            b.writeInt(196608);                                   // protocol 3.0
            cstring(b, "user");
            cstring(b, user);
            cstring(b, "database");
            cstring(b, database);
            b.writeByte(0);
            out.writeInt(body.size() + 4);
            out.write(body.toByteArray());
            out.flush();
            await(in, 'Z');                                        // ReadyForQuery after AuthenticationOk
            byte[] q = "SELECT 1\0".getBytes(StandardCharsets.US_ASCII);
            out.writeByte('Q');
            out.writeInt(q.length + 4);
            out.write(q);
            out.flush();
            await(in, 'Z');
            out.writeByte('X');
            out.writeInt(4);
            out.flush();
        }
    }

    private static void await(DataInputStream in, char type) throws IOException {
        while (true) {
            int t = in.readUnsignedByte();
            int len = in.readInt();
            byte[] payload = new byte[len - 4];
            in.readFully(payload);
            if (t == 'E') {
                throw new IOException("PostgreSQL error: " + new String(payload, StandardCharsets.UTF_8).replace('\0', ' ').trim());
            }
            if (t == 'R' && payload.length >= 4 && (payload[3] != 0 || payload[2] != 0)) {
                throw new IOException("PostgreSQL asked for a password; the lab check only supports trust authentication");
            }
            if (t == type) {
                return;
            }
        }
    }

    private static void cstring(DataOutputStream b, String s) throws IOException {
        b.write(s.getBytes(StandardCharsets.UTF_8));
        b.writeByte(0);
    }
}
