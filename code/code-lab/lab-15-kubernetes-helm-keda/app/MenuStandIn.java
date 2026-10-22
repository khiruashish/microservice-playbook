import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Lab 15's menu-service stand-in: the shape the shared chart expects of every FoodFleet service, in plain Java.
 *   8080  GET /menus/{id}   ~15 ms of CPU per request, so CPU follows load and the HPA has something to see
 *   8081  /actuator/health/liveness, /actuator/health/readiness (ADR-052); readiness fails for WARMUP_SECONDS
 * SIGTERM: readiness fails at once, requests in flight finish, then the JVM exits.
 * {@code MenuStandIn train}: the image build's training run for the AOT cache (ADR-060): 500 requests, then exit.
 */
public final class MenuStandIn {

    static final AtomicBoolean ready = new AtomicBoolean(false);
    static final AtomicBoolean stopping = new AtomicBoolean(false);

    public static void main(String[] args) throws Exception {
        long warmup = Long.parseLong(System.getenv().getOrDefault("WARMUP_SECONDS", "5"));
        HttpServer app = HttpServer.create(new InetSocketAddress(8080), 256);
        app.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        app.createContext("/menus/", ex -> reply(ex, 200, "{\"restaurant\":\"" + burn(ex.getRequestURI().getPath()) + "\"}"));
        HttpServer management = HttpServer.create(new InetSocketAddress(8081), 16);
        management.createContext("/actuator/health/liveness", ex -> reply(ex, 200, "{\"status\":\"UP\"}"));
        management.createContext("/actuator/health/readiness", ex -> reply(ex,
                ready.get() && !stopping.get() ? 200 : 503, ready.get() && !stopping.get() ? "{\"status\":\"UP\"}" : "{\"status\":\"OUT_OF_SERVICE\"}"));
        app.start();
        management.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            stopping.set(true);
            app.stop(5);                                    // finish what's in flight, up to 5 s
            management.stop(0);
        }));
        if (args.length > 0 && args[0].equals("train")) {
            var http = java.net.http.HttpClient.newHttpClient();
            for (int i = 0; i < 500; i++) {
                http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:8080/menus/r-" + i)).build(),
                        java.net.http.HttpResponse.BodyHandlers.discarding());
            }
            System.out.println("training run: 500 requests");
            System.exit(0);
        }
        System.out.println("menu-service stand-in: warming up for " + warmup + " s");
        Thread.sleep(warmup * 1000);
        ready.set(true);
        System.out.println("ready");
    }

    /** About 15 ms of CPU: repeated hashing, the kind of work a JSON-heavy endpoint does. */
    static String burn(String input) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] data = input.getBytes(StandardCharsets.UTF_8);
            long until = System.nanoTime() + 15_000_000;
            while (System.nanoTime() < until) {
                data = sha.digest(data);
            }
            return input.substring(input.lastIndexOf('/') + 1);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static void reply(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }
}
