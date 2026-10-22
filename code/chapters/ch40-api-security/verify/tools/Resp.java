import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** A minimal Redis (RESP2) client for the measurements: one connection, one command at a time. */
final class Resp implements AutoCloseable {

    static final class RedisError extends IOException {
        private static final long serialVersionUID = 1L;

        RedisError(String message) {
            super(message);
        }
    }

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;

    Resp(String host, int port) throws IOException {
        socket = new Socket(host, port);
        socket.setTcpNoDelay(true);
        socket.setSoTimeout(2000);
        in = new BufferedInputStream(socket.getInputStream());
        out = socket.getOutputStream();
    }

    Object call(String... args) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.writeBytes(("*" + args.length + "\r\n").getBytes(StandardCharsets.UTF_8));
        for (String a : args) {
            byte[] v = a.getBytes(StandardCharsets.UTF_8);
            b.writeBytes(("$" + v.length + "\r\n").getBytes(StandardCharsets.UTF_8));
            b.writeBytes(v);
            b.writeBytes("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        out.write(b.toByteArray());
        out.flush();
        return read();
    }

    /** EVAL with the script's integer results as longs. */
    List<Long> eval(String script, List<String> keys, List<String> args) throws IOException {
        List<String> a = new ArrayList<>();
        a.add("EVAL");
        a.add(script);
        a.add(Integer.toString(keys.size()));
        a.addAll(keys);
        a.addAll(args);
        Object r = call(a.toArray(String[]::new));
        List<Long> result = new ArrayList<>();
        for (Object o : (List<?>) r) {
            result.add(o instanceof Long l ? l : Long.parseLong(o.toString()));
        }
        return result;
    }

    private Object read() throws IOException {
        int type = in.read();
        String line = line();
        switch (type) {
            case '+':
                return line;
            case '-':
                throw new RedisError(line);
            case ':':
                return Long.parseLong(line);
            case '$': {
                int n = Integer.parseInt(line);
                if (n < 0) {
                    return null;
                }
                byte[] v = in.readNBytes(n);
                in.readNBytes(2);
                return new String(v, StandardCharsets.UTF_8);
            }
            case '*': {
                int n = Integer.parseInt(line);
                List<Object> items = new ArrayList<>(Math.max(n, 0));
                for (int i = 0; i < n; i++) {
                    items.add(read());
                }
                return items;
            }
            default:
                throw new IOException("unexpected RESP type " + type);
        }
    }

    private String line() throws IOException {
        StringBuilder s = new StringBuilder();
        int c;
        while ((c = in.read()) != '\r') {
            if (c < 0) {
                throw new java.io.EOFException();
            }
            s.append((char) c);
        }
        in.read();
        return s.toString();
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
