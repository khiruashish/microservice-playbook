package dev.foodfleet.ch26.experiment;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * An SSE server holding many open streams, run in its own JVM so its memory can be measured.
 * Executor: {@code virtual} (a virtual thread per request) or {@code pool200} (200 platform
 * threads, a common servlet-container default). On a separate admin port, {@code POST /broadcast}
 * sends one event to every open stream and {@code GET /stats} reports open streams, RSS and heap.
 * Usage: CapacityServer virtual|pool200 (prints the stream port and the admin port).
 */
public final class CapacityServer {

    static {
        System.setProperty("sun.net.httpserver.nodelay", "true");
    }

    private static final Set<LinkedBlockingQueue<String>> streams = ConcurrentHashMap.newKeySet();

    public static void main(String[] args) throws Exception {
        ExecutorService executor = "virtual".equals(args[0]) ? Executors.newVirtualThreadPerTaskExecutor()
                : Executors.newFixedThreadPool(200);
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 10_000);
        server.setExecutor(executor);
        server.createContext("/sse", CapacityServer::stream);
        // Admin endpoints on their own port and threads, so a saturated pool can still be measured.
        HttpServer admin = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        admin.setExecutor(Executors.newFixedThreadPool(2));
        admin.createContext("/broadcast", ex -> {
            String event = "data: " + System.nanoTime() + "\n\n";
            streams.forEach(q -> q.add(event));
            reply(ex, String.valueOf(streams.size()));
        });
        admin.createContext("/stats", ex -> {
            System.gc();
            long heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
            reply(ex, streams.size() + " " + rssKb() + " " + heap / 1024 + " " + Thread.activeCount());
        });
        server.start();
        admin.start();
        System.out.println(server.getAddress().getPort() + " " + admin.getAddress().getPort());
        System.out.flush();
        Thread.currentThread().join();
    }

    private static void stream(HttpExchange ex) throws IOException {
        ex.getResponseHeaders().set("Content-Type", "text/event-stream");
        ex.sendResponseHeaders(200, 0);
        LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>();
        streams.add(queue);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(": connected\n\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
            while (true) {
                String event = queue.poll(30, TimeUnit.SECONDS);
                out.write((event == null ? ": keep-alive\n\n" : event).getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
        } catch (IOException | InterruptedException e) {
            // client gone
        } finally {
            streams.remove(queue);
        }
    }

    private static long rssKb() throws IOException {
        for (String line : Files.readAllLines(Path.of("/proc/self/status"))) {
            if (line.startsWith("VmRSS:")) {
                return Long.parseLong(line.replaceAll("\\D+", ""));
            }
        }
        return -1;
    }

    private static void reply(HttpExchange ex, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }
}
