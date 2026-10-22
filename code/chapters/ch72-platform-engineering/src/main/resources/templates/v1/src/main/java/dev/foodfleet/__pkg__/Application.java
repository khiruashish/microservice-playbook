package dev.foodfleet.{{pkg}};

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/** {{name}}: {{description}} Owned by {{team}}. */
public final class Application {

    private Application() {
    }

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/actuator/health", ex -> reply(ex, 200, "{\"status\":\"UP\"}"));
        server.createContext("/hello", ex -> reply(ex, 200, "{\"service\":\"{{name}}\"}"));
        server.start();
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
