import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

/**
 * menu-service for the delivery experiments. {@code /menu} answers {@code version=<VERSION>}; with
 * {@code ERROR_RATE=0.05}, 5% of answers are 500s, the way a release with a bug on one code path fails. {@code /healthz}
 * always answers 200, so a release with that bug passes its readiness probe, as such releases do.
 */
public final class DeliveryLab {

    public static void main(String[] args) throws IOException {
        String version = System.getenv().getOrDefault("VERSION", "1.4.0");
        double errorRate = Double.parseDouble(System.getenv().getOrDefault("ERROR_RATE", "0"));
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 1024);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/menu", ex -> {
            boolean fail = ThreadLocalRandom.current().nextDouble() < errorRate;
            respond(ex, fail ? 500 : 200, "version=" + version + (fail ? " error" : ""));
        });
        server.createContext("/healthz", ex -> respond(ex, 200, "ok"));
        server.start();
        System.out.println("menu-service " + version + " started" + (errorRate > 0 ? " (error rate " + errorRate + ")" : ""));
    }

    static void respond(HttpExchange ex, int status, String body) {
        try {
            byte[] b = body.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status, b.length);
            ex.getResponseBody().write(b);
        } catch (IOException e) {
            // client went away
        } finally {
            ex.close();
        }
    }
}
