package dev.foodfleet.ch17.experiment;

import dev.foodfleet.ch17.ambassador.Ambassador;
import dev.foodfleet.ch17.ambassador.AmbassadorConfig;
import dev.foodfleet.ch17.fake.FakePaymentProvider;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Two measurements with real HTTP on loopback.
 *
 * <p><b>1. A provider having a bad day</b> (10% 503s, 3% of calls take 800 ms): kitchen-service
 * authorizes 5,000 payments, each with an Idempotency-Key, 16 at a time. Directly, with a 1 s
 * timeout and no retries (what most app code does), versus through the ambassador (400 ms per try,
 * up to 3 attempts).
 *
 * <p><b>2. The cost of the hop</b> on a healthy provider: 5,000 sequential calls, direct vs via the
 * ambassador. Loopback on one machine: the number is the proxy's own processing, not a network.
 */
public final class AmbassadorExperiment {

    public record Run(int requests, int succeeded, int authorizationsCreated, long p50Micros, long p99Micros,
                      long maxMicros) {
        double successRate() {
            return 100.0 * succeeded / requests;
        }
    }

    public static void main(String[] args) throws Exception {
        Path token = Files.createTempFile("payment-token", "");
        Files.writeString(token, "sk_live_demo");

        System.out.println("1. Provider with 10% 503s and 3% slow (800 ms) responses; 5,000 authorizations\n");
        System.out.printf("%-34s %9s %12s %9s %9s %9s%n", "path", "success", "auths made", "p50 ms", "p99 ms", "max ms");
        try (FakePaymentProvider provider = new FakePaymentProvider(0.10, 0.03, 800, 17);
             Ambassador ambassador = new Ambassador(config(provider, token))) {
            print("direct (1 s timeout, no retry)", run(provider.baseUrl(), 5_000, 16, Duration.ofSeconds(1), provider));
            provider.behave(0.10, 0.03, 800);
            int before = provider.authorizationsCreated();
            Run via = run("http://127.0.0.1:" + ambassador.port(), 5_000, 16, Duration.ofSeconds(3), provider);
            print("via ambassador (400 ms x 3 tries)", new Run(via.requests(), via.succeeded(),
                    provider.authorizationsCreated() - before, via.p50Micros(), via.p99Micros(), via.maxMicros()));
            System.out.println("\nambassador counters:\n" + ambassador.metrics().prometheus().indent(2));
        }

        System.out.println("2. Healthy provider: what the extra hop costs (5,000 sequential calls, loopback)\n");
        System.out.printf("%-34s %9s %9s%n", "path", "p50 us", "p99 us");
        try (FakePaymentProvider provider = new FakePaymentProvider(0, 0, 0, 17);
             Ambassador ambassador = new Ambassador(config(provider, token))) {
            run(provider.baseUrl(), 2_000, 1, Duration.ofSeconds(1), provider);                       // warm-up
            run("http://127.0.0.1:" + ambassador.port(), 2_000, 1, Duration.ofSeconds(1), provider);
            Run direct = run(provider.baseUrl(), 5_000, 1, Duration.ofSeconds(1), provider);
            Run via = run("http://127.0.0.1:" + ambassador.port(), 5_000, 1, Duration.ofSeconds(1), provider);
            System.out.printf("%-34s %9d %9d%n", "direct", direct.p50Micros(), direct.p99Micros());
            System.out.printf("%-34s %9d %9d%n", "via ambassador", via.p50Micros(), via.p99Micros());
            System.out.printf("%-34s %9d %9d%n", "added by the hop", via.p50Micros() - direct.p50Micros(),
                    via.p99Micros() - direct.p99Micros());
        }
        Files.deleteIfExists(token);
    }

    static AmbassadorConfig config(FakePaymentProvider provider, Path token) {
        return new AmbassadorConfig(0, URI.create(provider.baseUrl()), Duration.ofMillis(400), 3,
                Duration.ofMillis(10), token, 50, Duration.ofSeconds(5));
    }

    static Run run(String baseUrl, int requests, int concurrency, Duration timeout, FakePaymentProvider provider)
            throws Exception {
        int before = provider.authorizationsCreated();
        List<Long> latencies = Collections.synchronizedList(new ArrayList<>());
        List<Future<Boolean>> results = new ArrayList<>();
        try (HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
             ExecutorService pool = Executors.newFixedThreadPool(concurrency)) {
            for (int i = 0; i < requests; i++) {
                String key = "order-" + i + "-" + System.nanoTime();
                results.add(pool.submit(() -> {
                    HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/v1/authorizations"))
                            .timeout(timeout)
                            .header("Idempotency-Key", key)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString("{\"amountPaise\":45000}"))
                            .build();
                    long start = System.nanoTime();
                    try {
                        int status = client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
                        return status == 201;
                    } catch (IOException e) {
                        return false;
                    } finally {
                        latencies.add((System.nanoTime() - start) / 1_000);
                    }
                }));
            }
            int ok = 0;
            for (Future<Boolean> f : results) {
                if (f.get()) {
                    ok++;
                }
            }
            List<Long> sorted = new ArrayList<>(latencies);
            Collections.sort(sorted);
            return new Run(requests, ok, provider.authorizationsCreated() - before, percentile(sorted, 50),
                    percentile(sorted, 99), sorted.getLast());
        }
    }

    private static long percentile(List<Long> sorted, int p) {
        return sorted.get(Math.min(sorted.size() - 1, (int) Math.ceil(p / 100.0 * sorted.size()) - 1));
    }

    private static void print(String label, Run r) {
        System.out.printf("%-34s %8.2f%% %12d %9.1f %9.1f %9.1f%n", label, r.successRate(), r.authorizationsCreated(),
                r.p50Micros() / 1000.0, r.p99Micros() / 1000.0, r.maxMicros() / 1000.0);
    }
}
