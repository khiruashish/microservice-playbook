package dev.foodfleet.ch17.fake;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/** The restaurant-tablet gateway (a vendor box) and its home-grown /stats page. */
public final class FakeTabletGateway implements AutoCloseable {

    static {
        // The JDK server writes headers and body separately; without TCP_NODELAY, Nagle's algorithm
        // and the client's delayed ACK add ~40 ms to every response on a kept-alive connection.
        System.setProperty("sun.net.httpserver.nodelay", "true");
    }

    private final HttpServer server;
    private volatile String stats;

    public FakeTabletGateway(String stats) throws IOException {
        this.stats = stats;
        this.server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/stats", exchange -> {
            byte[] body = this.stats.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
    }

    public void stats(String stats) {
        this.stats = stats;
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
