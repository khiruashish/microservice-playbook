package dev.foodfleet.{{pkg}};

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/** {{name}}: {{description}} Owned by {{team}}. */
public final class Application {

    private static volatile boolean ready = true;

    private Application() {
    }

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/actuator/health/liveness", ex -> reply(ex, 200, "{\"status\":\"UP\"}"));
        server.createContext("/actuator/health/readiness", ex -> reply(ex, ready ? 200 : 503, ready ? "{\"status\":\"UP\"}" : "{\"status\":\"OUT_OF_SERVICE\"}"));
        server.createContext("/hello", ex -> reply(ex, 200, "{\"service\":\"{{name}}\"}"));
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            ready = false; // stop receiving traffic, then finish what's in progress
            try {
                Thread.sleep(5_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            server.stop(10);
        }));
    }

    static void reply(HttpExchange ex, int status, String body) throws IOException {
        try (ex) {
            byte[] b = body.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, b.length);
            ex.getResponseBody().write(b);
        }
    }
}
