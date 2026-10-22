import com.sun.net.httpserver.HttpServer;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * The lab for chapter 48: Micrometer registries served over HTTP the way Spring Boot's /actuator/prometheus serves
 * them, a real Prometheus (-Dc48.bin/prometheus) scraping them, and PromQL queries against it. Work files go to
 * -Dc48.dir (default /tmp/c48).
 */
final class Lab {

    private Lab() {
    }

    static final Path BIN = Path.of(System.getProperty("c48.bin", "build/bin")).toAbsolutePath();
    static final Path DIR = Path.of(System.getProperty("c48.dir", "/tmp/c48"));
    static final HttpClient HTTP = HttpClient.newBuilder().proxy(ProxySelector.of(null)).build();
    static int promPort;

    static void reset() throws IOException, InterruptedException {
        new ProcessBuilder("rm", "-rf", DIR.toString()).start().waitFor();
        Files.createDirectories(DIR);
    }

    static int freePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    /** Serves {@code registry.scrape()} at /metrics, like /actuator/prometheus. Returns the port. */
    static int serve(PrometheusMeterRegistry registry) throws IOException {
        int port = freePort();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 16);
        server.createContext("/metrics", ex -> {
            byte[] body = registry.scrape().getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "text/plain; version=0.0.4; charset=utf-8");
            ex.sendResponseHeaders(200, body.length);
            try (OutputStream o = ex.getResponseBody()) {
                o.write(body);
            }
        });
        server.start();
        return port;
    }

    /** Starts Prometheus scraping {@code targets} (instance label -> port) every {@code interval}. */
    static Process prometheus(Map<String, Integer> targets, String interval) throws IOException, InterruptedException {
        promPort = freePort();
        StringBuilder cfg = new StringBuilder("""
                global:
                  scrape_interval: %s
                  scrape_timeout: %s
                scrape_configs:
                  - job_name: order-service
                    static_configs:
                """.formatted(interval, interval));
        targets.forEach((instance, port) -> cfg.append("      - targets: ['127.0.0.1:").append(port)
                .append("']\n        labels: {pod: ").append(instance).append("}\n"));
        Path file = DIR.resolve("prometheus.yml");
        Files.writeString(file, cfg);
        Process p = new ProcessBuilder(BIN.resolve("prometheus").toString(), "--config.file=" + file,
                "--storage.tsdb.path=" + DIR.resolve("data"), "--web.listen-address=127.0.0.1:" + promPort,
                "--log.level=warn").redirectErrorStream(true).redirectOutput(DIR.resolve("prometheus.log").toFile()).start();
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        while (System.nanoTime() < end) {
            try {
                if (HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + promPort + "/-/ready")).build(),
                        HttpResponse.BodyHandlers.discarding()).statusCode() == 200) {
                    return p;
                }
            } catch (IOException e) {
                // not yet
            }
            Thread.sleep(100);
        }
        throw new IllegalStateException("prometheus not ready");
    }

    static void stop(Process p) throws InterruptedException {
        p.destroy();
        if (!p.waitFor(20, TimeUnit.SECONDS)) {
            p.destroyForcibly().waitFor();
        }
    }

    /** A PromQL instant query at {@code atSeconds} (Unix time); returns the first sample's value, or NaN. */
    static double query(String promql, double atSeconds) throws IOException, InterruptedException {
        String url = "http://127.0.0.1:" + promPort + "/api/v1/query?query=" + URLEncoder.encode(promql, StandardCharsets.UTF_8)
                + "&time=" + String.format("%.3f", atSeconds);
        String body = HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).build(),
                HttpResponse.BodyHandlers.ofString()).body();
        if (!body.contains("\"status\":\"success\"")) {
            throw new IllegalStateException(promql + " -> " + body);
        }
        int v = body.indexOf("\"value\":[");
        if (v < 0) {
            return Double.NaN;
        }
        int q1 = body.indexOf('"', body.indexOf(',', v) + 1);
        int q2 = body.indexOf('"', q1 + 1);
        return Double.parseDouble(body.substring(q1 + 1, q2));
    }

    static double percentile(List<Double> values, double p) {
        if (values.isEmpty()) {
            return Double.NaN;
        }
        List<Double> s = new ArrayList<>(values);
        Collections.sort(s);
        int i = (int) Math.ceil(p / 100.0 * s.size()) - 1;
        return s.get(Math.max(0, Math.min(s.size() - 1, i)));
    }

    /** Heap in use after a full collection, in bytes. */
    static long usedHeap() {
        for (int i = 0; i < 3; i++) {
            System.gc();
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        Runtime r = Runtime.getRuntime();
        return r.totalMemory() - r.freeMemory();
    }

    /** Series in a Prometheus text exposition: non-comment, non-empty lines. */
    static long series(String scrape) {
        return scrape.lines().filter(l -> !l.isEmpty() && l.charAt(0) != '#').count();
    }
}
