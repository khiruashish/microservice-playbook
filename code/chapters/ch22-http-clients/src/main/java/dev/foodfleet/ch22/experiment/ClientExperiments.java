package dev.foodfleet.ch22.experiment;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Three things that decide how an HTTP client behaves in production, measured with the JDK's
 * HttpClient (one of the engines Spring's RestClient can sit on) against a local server.
 *
 * <ol>
 *   <li><b>Reuse</b>: one shared client (pooled keep-alive connections) vs a new client per call
 *       (a new TCP connection and TLS handshake every time), over HTTPS.</li>
 *   <li><b>Timeouts</b>: a call to an endpoint that never answers in time, with and without a request
 *       timeout. The JDK client's default is no timeout.</li>
 *   <li><b>Concurrency</b>: 1,000 calls to a 200 ms endpoint from a 50-thread pool, from virtual
 *       threads, and with the asynchronous API.</li>
 * </ol>
 */
public final class ClientExperiments {

    public record Latency(int calls, double p50Ms, double p99Ms, double totalMs) {
    }

    static HttpClient client(LocalServer server) {
        HttpClient.Builder b = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(2));
        if (server.clientSslContext() != null) {
            b.sslContext(server.clientSslContext());
        }
        return b.build();
    }

    static HttpRequest get(LocalServer server, String path, Duration timeout) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(server.baseUrl() + path));
        if (timeout != null) {
            b.timeout(timeout);
        }
        return b.build();
    }

    /** Sequential calls; either one shared client or a fresh client per call. */
    public static Latency reuse(LocalServer server, int calls, boolean shared) throws Exception {
        double[] ms = new double[calls];
        long start = System.nanoTime();
        try (HttpClient sharedClient = client(server)) {
            for (int i = 0; i < calls; i++) {
                long t = System.nanoTime();
                if (shared) {
                    sharedClient.send(get(server, "/fast", Duration.ofSeconds(2)), HttpResponse.BodyHandlers.ofString());
                } else {
                    try (HttpClient fresh = client(server)) {
                        fresh.send(get(server, "/fast", Duration.ofSeconds(2)), HttpResponse.BodyHandlers.ofString());
                    }
                }
                ms[i] = (System.nanoTime() - t) / 1e6;
            }
        }
        double total = (System.nanoTime() - start) / 1e6;
        Arrays.sort(ms);
        return new Latency(calls, ms[calls / 2], ms[(int) (calls * 0.99)], total);
    }

    /** How long the caller is stuck on a hanging endpoint; we stop watching after {@code giveUp}. */
    public static String hang(LocalServer server, Duration requestTimeout, Duration giveUp) throws Exception {
        // Not try-with-resources: HttpClient.close() waits for in-flight requests, which here means
        // waiting for the hung one. shutdownNow() aborts them.
        HttpClient c = client(server);
        try (ExecutorService caller = Executors.newVirtualThreadPerTaskExecutor()) {
            long start = System.nanoTime();
            Future<String> call = caller.submit(() -> {
                try {
                    return "answered " + c.send(get(server, "/hang", requestTimeout),
                            HttpResponse.BodyHandlers.ofString()).statusCode();
                } catch (HttpTimeoutException e) {
                    return "HttpTimeoutException";
                }
            });
            try {
                String outcome = call.get(giveUp.toMillis(), TimeUnit.MILLISECONDS);
                return "%s after %.0f ms".formatted(outcome, (System.nanoTime() - start) / 1e6);
            } catch (TimeoutException e) {
                call.cancel(true);
                return "still blocked after %.0f ms (a request thread, a connection and a caller held)"
                        .formatted((System.nanoTime() - start) / 1e6);
            } finally {
                c.shutdownNow();
            }
        }
    }

    public enum Style { PLATFORM_POOL_50, VIRTUAL_THREADS, ASYNC_API }

    /** {@code calls} concurrent requests to the 200 ms endpoint; total wall time. */
    public static double concurrency(LocalServer server, Style style, int calls) throws Exception {
        try (HttpClient c = client(server)) {
            // warm the connection pool a little so all styles start from the same place
            c.send(get(server, "/fast", Duration.ofSeconds(2)), HttpResponse.BodyHandlers.ofString());
            long start = System.nanoTime();
            switch (style) {
                case PLATFORM_POOL_50, VIRTUAL_THREADS -> {
                    try (ExecutorService pool = style == Style.PLATFORM_POOL_50
                            ? Executors.newFixedThreadPool(50) : Executors.newVirtualThreadPerTaskExecutor()) {
                        List<Future<Integer>> futures = new ArrayList<>();
                        for (int i = 0; i < calls; i++) {
                            futures.add(pool.submit(() -> c.send(get(server, "/slow", Duration.ofSeconds(30)),
                                    HttpResponse.BodyHandlers.ofString()).statusCode()));
                        }
                        for (Future<Integer> f : futures) {
                            check(f.get());
                        }
                    }
                }
                case ASYNC_API -> {
                    List<CompletableFuture<HttpResponse<String>>> futures = new ArrayList<>();
                    for (int i = 0; i < calls; i++) {
                        futures.add(c.sendAsync(get(server, "/slow", Duration.ofSeconds(30)),
                                HttpResponse.BodyHandlers.ofString()));
                    }
                    for (CompletableFuture<HttpResponse<String>> f : futures) {
                        check(f.get().statusCode());
                    }
                }
            }
            return (System.nanoTime() - start) / 1e6;
        }
    }

    private static void check(int status) throws IOException {
        if (status != 200) {
            throw new IOException("unexpected status " + status);
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("1. Reuse over HTTPS: 300 sequential GETs to a local server");
        System.out.printf("   %-34s %8s %8s %10s%n", "", "p50 ms", "p99 ms", "total ms");
        try (LocalServer https = LocalServer.https()) {
            reuse(https, 50, true);                                   // warm-up (JIT, TLS provider)
            reuse(https, 20, false);
            Latency shared = reuse(https, 300, true);
            Latency fresh = reuse(https, 300, false);
            System.out.printf("   %-34s %8.2f %8.2f %10.0f%n", "one shared client (keep-alive)", shared.p50Ms(),
                    shared.p99Ms(), shared.totalMs());
            System.out.printf("   %-34s %8.2f %8.2f %10.0f%n", "new client per call (new TLS)", fresh.p50Ms(),
                    fresh.p99Ms(), fresh.totalMs());
        }

        System.out.println("\n2. Timeouts: GET /hang (the server takes 60 s)");
        try (LocalServer http = LocalServer.http()) {
            System.out.println("   no request timeout:    " + hang(http, null, Duration.ofSeconds(3)));
            System.out.println("   300 ms request timeout: " + hang(http, Duration.ofMillis(300), Duration.ofSeconds(3)));
        }

        System.out.println("\n3. Concurrency: 1,000 calls to an endpoint that takes 200 ms");
        try (LocalServer http = LocalServer.http()) {
            concurrency(http, Style.VIRTUAL_THREADS, 200);           // warm-up
            for (Style style : Style.values()) {
                double ms = concurrency(http, style, 1_000);
                System.out.printf("   %-34s %8.0f ms%n", style, ms);
            }
            System.out.println("   server's peak concurrent requests: " + http.peakInFlight());
        }
    }
}
