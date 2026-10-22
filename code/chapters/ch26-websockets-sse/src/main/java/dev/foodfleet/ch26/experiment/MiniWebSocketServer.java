package dev.foodfleet.ch26.experiment;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Just enough of RFC 6455 to push text frames to a client: the HTTP upgrade handshake, unmasked
 * server frames, and answering the client's close. The JDK's WebSocket client is the other end, so
 * the handshake and framing are checked by a real implementation.
 */
public final class MiniWebSocketServer implements AutoCloseable {

    static final String GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    private final ServerSocket server;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final PositionFeed feed;

    public MiniWebSocketServer(PositionFeed feed) throws IOException {
        this.feed = feed;
        this.server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        executor.submit(this::acceptLoop);
    }

    public int port() {
        return server.getLocalPort();
    }

    /** Sec-WebSocket-Accept for a client's Sec-WebSocket-Key (RFC 6455, section 4.2.2). */
    public static String accept(String key) throws Exception {
        byte[] sha1 = MessageDigest.getInstance("SHA-1").digest((key + GUID).getBytes(StandardCharsets.US_ASCII));
        return Base64.getEncoder().encodeToString(sha1);
    }

    /** A FIN text frame from server to client: 2-byte header (4 for 126-65535 bytes), no mask. */
    public static byte[] textFrame(String text) {
        byte[] payload = text.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0x81);
        if (payload.length < 126) {
            out.write(payload.length);
        } else {
            out.write(126);
            out.write(payload.length >>> 8);
            out.write(payload.length & 0xff);
        }
        out.writeBytes(payload);
        return out.toByteArray();
    }

    private void acceptLoop() {
        while (!server.isClosed()) {
            try {
                Socket socket = server.accept();
                socket.setTcpNoDelay(true);
                executor.submit(() -> handle(socket));
            } catch (IOException e) {
                return;
            }
        }
    }

    private void handle(Socket socket) {
        try (socket) {
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();
            String request = readHeaders(in);
            String key = null;
            for (String line : request.split("\r\n")) {
                if (line.toLowerCase(Locale.ROOT).startsWith("sec-websocket-key:")) {
                    key = line.substring(line.indexOf(':') + 1).trim();
                }
            }
            if (key == null) {
                out.write("HTTP/1.1 400 Bad Request\r\nContent-Length: 0\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                return;
            }
            out.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                    + "Sec-WebSocket-Accept: " + accept(key) + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            out.flush();
            Object lock = new Object();
            Runnable unsubscribe = feed.subscribe(u -> {
                synchronized (lock) {
                    try {
                        out.write(textFrame(u.json()));
                        out.flush();
                    } catch (IOException ignored) {
                        // client gone; the read loop below ends too
                    }
                }
            });
            try {
                readUntilClose(in, out, lock);
            } finally {
                unsubscribe.run();
            }
        } catch (Exception ignored) {
            // connection closed
        }
    }

    /** Reads client frames (always masked) until a close frame, which is echoed back. */
    private static void readUntilClose(InputStream in, OutputStream out, Object lock) throws IOException {
        while (true) {
            int b0 = in.read();
            int b1 = in.read();
            if (b0 < 0 || b1 < 0) {
                return;
            }
            long len = b1 & 0x7f;
            if (len == 126) {
                len = (in.read() << 8) | in.read();
            } else if (len == 127) {
                len = 0;
                for (int i = 0; i < 8; i++) {
                    len = (len << 8) | in.read();
                }
            }
            byte[] mask = (b1 & 0x80) != 0 ? in.readNBytes(4) : null;
            byte[] payload = in.readNBytes((int) len);
            if (mask != null) {
                for (int i = 0; i < payload.length; i++) {
                    payload[i] ^= mask[i % 4];
                }
            }
            if ((b0 & 0x0f) == 0x8) {                        // close: echo the status code and stop
                synchronized (lock) {
                    out.write(0x88);
                    out.write(payload.length);
                    out.write(payload);
                    out.flush();
                }
                return;
            }
        }
    }

    private static String readHeaders(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int matched = 0;
        int b;
        while ((b = in.read()) >= 0) {
            buf.write(b);
            matched = (b == "\r\n\r\n".charAt(matched)) ? matched + 1 : (b == '\r' ? 1 : 0);
            if (matched == 4) {
                break;
            }
        }
        return buf.toString(StandardCharsets.US_ASCII);
    }

    @Override
    public void close() throws IOException {
        server.close();
        executor.shutdownNow();
    }
}
