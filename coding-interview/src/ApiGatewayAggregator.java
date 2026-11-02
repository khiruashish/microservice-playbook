import java.util.*;
import java.util.concurrent.*;

/** Problem: GET /home aggregates profile, recommendations and offers in parallel; each has a timeout and a fallback. */
public class ApiGatewayAggregator {

    record HomePage(String profile, List<String> recommendations, List<String> offers, List<String> degraded) { }

    static CompletableFuture<String> call(ExecutorService pool, String name, long latencyMs, boolean fails) {
        return CompletableFuture.supplyAsync(() -> {
            try { Thread.sleep(latencyMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            if (fails) throw new IllegalStateException(name + " 500");
            return name + "-data";
        }, pool);
    }

    static HomePage aggregate(ExecutorService pool, long recsLatency, boolean offersFail) {
        List<String> degraded = Collections.synchronizedList(new ArrayList<>());

        // Required: no fallback, the whole page fails if profile fails.
        CompletableFuture<String> profile = call(pool, "profile", 20, false).orTimeout(500, TimeUnit.MILLISECONDS);

        // Optional: a timeout plus a fallback value, and the response says which parts are degraded.
        CompletableFuture<List<String>> recs = call(pool, "recommendations", recsLatency, false)
                .thenApply(List::of)
                .completeOnTimeout(null, 100, TimeUnit.MILLISECONDS)
                .thenApply(r -> { if (r == null) { degraded.add("recommendations: timeout"); return List.of("bestsellers (fallback)"); } return r; });

        CompletableFuture<List<String>> offers = call(pool, "offers", 10, offersFail)
                .thenApply(List::of)
                .exceptionally(e -> { degraded.add("offers: " + e.getCause().getMessage()); return List.of(); });

        // Fan-in: total latency ~= the slowest call (bounded by its timeout), not the sum
        return CompletableFuture.allOf(profile, recs, offers)
                .thenApply(v -> new HomePage(profile.join(), recs.join(), offers.join(), new TreeSet<>(degraded).stream().toList()))
                .join();
    }

    public static void main(String[] args) {
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            long t0 = System.nanoTime();
            HomePage happy = aggregate(pool, 30, false);
            long happyMs = (System.nanoTime() - t0) / 1_000_000;
            System.out.println("all healthy : " + happy);

            t0 = System.nanoTime();
            HomePage degraded = aggregate(pool, 2_000, true);
            long degradedMs = (System.nanoTime() - t0) / 1_000_000;
            System.out.println("degraded    : " + degraded);
            System.out.println("slow recs (2s) did not make the page slow: answered within the 100 ms budget + margin: " + (degradedMs < 1_000));

            check(happy.degraded().isEmpty() && happy.offers().equals(List.of("offers-data")), "happy path");
            check(degraded.recommendations().equals(List.of("bestsellers (fallback)")) && degraded.offers().isEmpty(), "fallbacks");
            check(degraded.degraded().size() == 2, "degradation reported to the client");
            check(happyMs < 1_000 && degradedMs < 1_000, "parallel, bounded latency");
        }
        System.out.println("PASS");
    }

    static void check(boolean ok, String what) { if (!ok) { System.out.println("FAILED: " + what); System.exit(1); } }
}
