package dev.foodfleet.ch11.kitchen;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

/** Tiny kitchen-service instances on random ports, and a port where nothing listens. */
final class KitchenStubs {

    private KitchenStubs() {
    }

    record Stub(HttpServer server, AtomicInteger hits) {
        String uri() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }
    }

    static Stub start(String name) {
        try {
            AtomicInteger hits = new AtomicInteger();
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/api/kitchen/capacity", exchange -> {
                hits.incrementAndGet();
                byte[] body = ("{\"instance\":\"" + name + "\",\"openTickets\":7}").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            });
            server.start();
            return new Stub(server, hits);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A port that was free a moment ago: connecting to it is refused, like a crashed instance. */
    static String deadInstanceUri() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return "http://127.0.0.1:" + socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
