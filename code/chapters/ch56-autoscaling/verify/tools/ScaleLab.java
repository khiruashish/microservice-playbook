import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/**
 * menu-service for the autoscaling experiments.
 * <ul>
 *   <li>{@code /work?ms=N}: N ms of CPU time, so load is CPU the HPA can see</li>
 *   <li>{@code /ready}: the readiness probe; with {@code READY_AFTER_WARMUP=true} it fails until warm-up is over</li>
 * </ul>
 * {@code WARMUP_SECONDS} and {@code WARMUP_THREADS} burn CPU at startup, the way a cold JVM's class loading and JIT
 * compilation do for a Spring Boot service (chapter 54 measured 0.7–4 s of startup CPU for a small one; real services
 * take longer and keep compiling for minutes).
 */
public final class ScaleLab {

    static final ThreadMXBean THREADS = ManagementFactory.getThreadMXBean();
    static volatile boolean warm;

    public static void main(String[] args) throws Exception {
        int warmup = Integer.parseInt(System.getenv().getOrDefault("WARMUP_SECONDS", "0"));
        int threads = Integer.parseInt(System.getenv().getOrDefault("WARMUP_THREADS", "1"));
        boolean gate = Boolean.parseBoolean(System.getenv().getOrDefault("READY_AFTER_WARMUP", "true"));
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 512);
        server.setExecutor(Executors.newFixedThreadPool(16));                 // platform threads: per-thread CPU time
        server.createContext("/menu", ex -> respond(ex, 200, "{\"items\":42}"));
        server.createContext("/ready", ex -> respond(ex, warm || !gate ? 200 : 503, warm ? "warm" : "warming up"));
        server.createContext("/work", ex -> {
            String q = ex.getRequestURI().getQuery();
            burn(q == null ? 10 : Long.parseLong(q.substring(q.indexOf('=') + 1)));
            respond(ex, 200, "done");
        });
        server.start();
        if (warmup > 0) {
            Thread[] ts = new Thread[threads];
            for (int i = 0; i < threads; i++) {
                ts[i] = Thread.ofPlatform().start(() -> burn(warmup * 1000L));
            }
            for (Thread t : ts) {
                t.join();
            }
        }
        warm = true;
        System.out.println("menu-service warm after " + (System.currentTimeMillis() - ManagementFactory.getRuntimeMXBean().getStartTime()) + " ms");
    }

    /** Spends {@code ms} of this thread's CPU time (wall-clock time where per-thread CPU time isn't available). */
    static void burn(long ms) {
        boolean cpu = THREADS.getCurrentThreadCpuTime() >= 0;
        long start = cpu ? THREADS.getCurrentThreadCpuTime() : System.nanoTime();
        long x = 0;
        while ((cpu ? THREADS.getCurrentThreadCpuTime() : System.nanoTime()) - start < ms * 1_000_000) {
            for (int i = 0; i < 10_000; i++) {
                x += i * 31L ^ x;
            }
        }
        if (x == 42) {
            System.out.print("");
        }
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
