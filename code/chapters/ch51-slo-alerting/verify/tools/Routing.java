import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The chapter's alertmanager.yml on a real Alertmanager 0.34.1: its receivers pointed at a local webhook, its waits
 * shortened from minutes to seconds, and SLO alerts posted to its API the way Prometheus posts them. Shows where each
 * alert goes and that a page silences the ticket for the same SLO until the page resolves.
 */
final class Routing {

    private Routing() {
    }

    static final HttpClient HTTP = HttpClient.newBuilder().proxy(ProxySelector.of(null)).build();
    static final List<String> RECEIVED = new ArrayList<>();
    static volatile long t0;

    static void run() throws Exception {
        String am = System.getProperty("c51.alertmanager", "build/bin/alertmanager");
        Path config = Path.of(System.getProperty("c51.amconfig", "../src/main/resources/alertmanager/alertmanager.yml"));
        int hook = free();
        int port = free();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", hook), 16);
        server.createContext("/", ex -> {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String path = ex.getRequestURI().getPath();
            List<Object> alerts = Json.arr(Json.at(Json.parse(body), "alerts"));
            StringBuilder s = new StringBuilder();
            for (Object a : alerts) {
                s.append(s.isEmpty() ? "" : ", ").append(Json.at(a, "labels", "slo")).append('/').append(Json.at(a, "labels", "severity"))
                        .append(' ').append(Json.at(a, "status"));
            }
            synchronized (RECEIVED) {
                RECEIVED.add(String.format("%5.1f s  %-8s %s (%s)", (System.nanoTime() - t0) / 1e9, path, s, Json.at(Json.parse(body), "receiver")));
            }
            ex.sendResponseHeaders(200, -1);
            ex.close();
        });
        server.start();
        String yaml = Files.readString(config)
                .replace("http://incident-bridge.monitoring.svc:8080", "http://127.0.0.1:" + hook)
                .replaceAll("group_wait: \\d+[smh]", "group_wait: 2s")
                .replaceAll("group_interval: \\d+[smh]", "group_interval: 3s");
        Path dir = Measure.DIR.resolve("am");
        new ProcessBuilder("rm", "-rf", dir.toString()).start().waitFor();   // no notification log from an earlier run
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("alertmanager.yml"), yaml);
        Process p = new ProcessBuilder(am, "--config.file=" + dir.resolve("alertmanager.yml"), "--storage.path=" + dir.resolve("data"),
                "--web.listen-address=127.0.0.1:" + port, "--cluster.listen-address=")
                .redirectErrorStream(true).redirectOutput(dir.resolve("alertmanager.log").toFile()).start();
        try {
            waitReady(port);
            System.out.println("Alertmanager 0.34.1 with alertmanager.yml (group_wait 2s, group_interval 3s for the test)");
            t0 = System.nanoTime();
            Instant now = Instant.now();
            post(port, alert("checkout-availability", "page", now, null), alert("checkout-availability", "ticket", now, null),
                    alert("checkout-latency", "ticket", now, null));
            System.out.println("  0.0 s  posted: checkout-availability page and ticket, checkout-latency ticket");
            Thread.sleep(7000);
            System.out.printf("%5.1f s  posted: checkout-availability page resolved%n", (System.nanoTime() - t0) / 1e9);
            post(port, alert("checkout-availability", "page", now, Instant.now()), alert("checkout-availability", "ticket", now, null),
                    alert("checkout-latency", "ticket", now, null));
            Thread.sleep(9000);
            synchronized (RECEIVED) {
                System.out.println("webhooks received:");
                RECEIVED.forEach(r -> System.out.println("  " + r));
            }
        } finally {
            p.destroy();
            p.waitFor(10, TimeUnit.SECONDS);
            server.stop(0);
        }
        System.out.println();
    }

    static String alert(String slo, String severity, Instant start, Instant end) {
        return """
                {"labels": {"alertname": "%s", "severity": "%s", "slo": "%s", "service": "order-service"},
                 "startsAt": "%s"%s}""".formatted(slo.equals("checkout-latency") ? "CheckoutLatencyErrorBudgetBurn" : "CheckoutAvailabilityErrorBudgetBurn",
                severity, slo, start, end == null ? "" : ", \"endsAt\": \"" + end + "\"");
    }

    static void post(int port, String... alerts) throws Exception {
        HttpResponse<String> r = HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v2/alerts"))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("[" + String.join(",", alerts) + "]"))
                .build(), HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() != 200) {
            throw new IllegalStateException("Alertmanager said " + r.statusCode() + ": " + r.body());
        }
    }

    static void waitReady(int port) throws Exception {
        for (int i = 0; i < 300; i++) {
            try {
                if (HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/-/ready")).build(),
                        HttpResponse.BodyHandlers.discarding()).statusCode() == 200) {
                    return;
                }
            } catch (java.io.IOException e) {
                // starting
            }
            Thread.sleep(100);
        }
        throw new IllegalStateException("Alertmanager didn't start");
    }

    static int free() throws Exception {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }
}
