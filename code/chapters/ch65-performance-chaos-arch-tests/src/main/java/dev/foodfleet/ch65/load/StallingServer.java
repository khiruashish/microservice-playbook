package dev.foodfleet.ch65.load;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A service with a fixed number of worker threads, each request taking {@code serviceMillis}, that freezes for
 * {@code stallMillis} every {@code periodMillis}: every request in flight or arriving during the freeze waits until
 * it ends, as in a stop-the-world GC pause, a failover or a lock held by a nightly job.
 */
public final class StallingServer implements AutoCloseable {

    private final HttpServer server;
    private final ExecutorService workers;
    private final long start = System.nanoTime();

    public StallingServer(int workers, long serviceMillis, long periodMillis, long stallMillis) throws IOException {
        System.setProperty("sun.net.httpserver.nodelay", "true");
        this.workers = Executors.newFixedThreadPool(workers);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 4096);
        server.setExecutor(this.workers);
        server.createContext("/", ex -> {
            try (ex) {
                if (stallMillis > 0) {
                    long phase = ((System.nanoTime() - start) / 1_000_000) % periodMillis;
                    if (phase >= periodMillis - stallMillis) {
                        Thread.sleep(periodMillis - phase);
                    }
                }
                Thread.sleep(serviceMillis);
                byte[] body = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(200, body.length);
                ex.getResponseBody().write(body);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        server.start();
    }

    public String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/menu";
    }

    @Override
    public void close() {
        server.stop(0);
        workers.shutdownNow();
    }
}
