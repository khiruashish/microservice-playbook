package dev.foodfleet.ch17.adapter;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * An adapter sidecar: Prometheus scrapes {@code :9102/metrics} like any other pod; on each scrape
 * the adapter reads the vendor gateway's /stats and translates it. The vendor box is unchanged and
 * the platform sees one standard interface.
 */
public final class MetricsAdapter implements AutoCloseable {

    static {
        // The JDK server writes headers and body separately; without TCP_NODELAY, Nagle's algorithm
        // and the client's delayed ACK add ~40 ms to every response on a kept-alive connection.
        System.setProperty("sun.net.httpserver.nodelay", "true");
    }

    private final HttpServer server;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(1)).build();
    private final TabletStatsTranslator translator = new TabletStatsTranslator();

    public MetricsAdapter(int port, URI legacyStats) throws IOException {
        // Bound on all interfaces: Prometheus scrapes it from outside the pod.
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(executor);
        server.createContext("/metrics", exchange -> {
            String page;
            try {
                HttpResponse<String> stats = client.send(HttpRequest.newBuilder(legacyStats)
                        .timeout(Duration.ofSeconds(2)).build(), HttpResponse.BodyHandlers.ofString());
                page = stats.statusCode() == 200 ? translator.translate(stats.body()) : translator.unreachable();
            } catch (IOException e) {
                page = translator.unreachable();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                page = translator.unreachable();
            }
            byte[] body = page.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/plain; version=0.0.4; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
        client.close();
        executor.close();
    }

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("LISTEN_PORT", "9102"));
        URI stats = URI.create(System.getenv().getOrDefault("LEGACY_STATS_URL", "http://tablet-gateway.local/stats"));
        new MetricsAdapter(port, stats);
        System.out.println("metrics adapter on :" + port + " translating " + stats);
    }
}
