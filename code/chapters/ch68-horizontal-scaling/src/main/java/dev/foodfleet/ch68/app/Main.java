package dev.foodfleet.ch68.app;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

/**
 * The two services chapter 68 scales, in one image.
 * <ul>
 *   <li>{@code order}: carts ({@code POST /cart/{user}}, {@code GET /cart/{user}}) kept in memory or in PostgreSQL
 *       ({@code CARTS=memory|postgres}), and {@code GET /work}: about 5 ms of CPU, then optionally a call to the ledger
 *       ({@code LEDGER_URL}). Every answer says which copy served it ({@code X-Pod}).</li>
 *   <li>{@code ledger}: a dependency every copy shares, with fixed capacity: {@code PERMITS} calls at a time (2), each
 *       held {@code HOLD_MS} (40 ms), the rest wait: at most 50 calls a second. A database or a payment provider plays this part in production.</li>
 * </ul>
 */
public final class Main {

    static final String POD = hostname();
    static final ThreadMXBean THREADS = ManagementFactory.getThreadMXBean();

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("sun.net.httpserver.nodelay", "true");
        System.setProperty("sun.net.httpserver.maxIdleConnections", "4000");
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 1024);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        switch (args[0]) {
            case "order" -> {
                calibrate();
                order(server);
            }
            case "ledger" -> ledger(server, Integer.parseInt(System.getenv().getOrDefault("PERMITS", "2")),
                    Integer.parseInt(System.getenv().getOrDefault("HOLD_MS", "40")));
            default -> throw new IllegalArgumentException(args[0]);
        }
        server.createContext("/ready", ex -> reply(ex, 200, "ok"));
        server.start();
    }

    static void order(HttpServer server) throws Exception {
        Carts carts = "postgres".equals(System.getenv("CARTS"))
                ? new Carts.Shared(System.getenv("DB_URL"), "postgres", "postgres", 8)
                : new Carts.InMemory();
        String ledger = System.getenv("LEDGER_URL") == null || System.getenv("LEDGER_URL").isBlank() ? null : System.getenv("LEDGER_URL");
        HttpClient http = HttpClient.newBuilder().executor(Executors.newVirtualThreadPerTaskExecutor()).build();
        server.createContext("/cart/", ex -> {
            String user = ex.getRequestURI().getPath().substring("/cart/".length());
            try {
                int n = "POST".equals(ex.getRequestMethod()) ? carts.add(user) : carts.count(user);
                reply(ex, 200, String.valueOf(n));
            } catch (Exception e) {
                reply(ex, 500, e.toString());
            }
        });
        server.createContext("/work", ex -> {
            burnCpu(5);
            try {
                if (ledger != null) {
                    http.send(HttpRequest.newBuilder(URI.create(ledger + "/ledger")).build(), HttpResponse.BodyHandlers.discarding());
                }
                reply(ex, 200, "done");
            } catch (Exception e) {
                reply(ex, 502, e.toString());
            }
        });
    }

    /** Loop iterations per millisecond of CPU, measured once at startup on a platform thread. */
    static volatile long iterationsPerMs;

    /**
     * Calibrates {@link #spin}: virtual threads can't read their own CPU time (it reads -1), so the loop's speed is
     * measured on this platform thread, after the JIT has compiled it, and requests then run a fixed number of rounds.
     */
    static void calibrate() {
        long best = 0;
        for (int round = 0; round < 30; round++) {
            long t = THREADS.getCurrentThreadCpuTime();
            spin(1_000_000);
            long cpu = THREADS.getCurrentThreadCpuTime() - t;
            best = Math.max(best, 1_000_000L * 1_000_000 / Math.max(1, cpu));
        }
        iterationsPerMs = best;
    }

    static long spin(long iterations) {
        long x = 0;
        for (long i = 0; i < iterations; i++) {
            x = x * 6364136223846793005L + 1442695040888963407L;
        }
        return x;
    }

    /** Uses about this many milliseconds of CPU, however long that takes on a throttled container. */
    public static long burnCpu(int millis) {
        return spin(iterationsPerMs * millis);
    }

    static void ledger(HttpServer server, int permits, int holdMillis) {
        Semaphore inUse = new Semaphore(permits, true);
        server.createContext("/ledger", ex -> {
            try {
                inUse.acquire();
                try {
                    Thread.sleep(holdMillis);
                } finally {
                    inUse.release();
                }
                reply(ex, 200, "posted");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    static void reply(HttpExchange ex, int status, String body) throws IOException {
        try (ex) {
            byte[] b = body.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("X-Pod", POD);
            if ("close".equalsIgnoreCase(ex.getRequestHeaders().getFirst("Connection"))) {
                ex.getResponseHeaders().set("Connection", "close"); // so the client doesn't put the connection back in its pool
            }
            ex.sendResponseHeaders(status, b.length);
            ex.getResponseBody().write(b);
        }
    }

    static String hostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
