package dev.foodfleet.ch20.chatty;

import java.util.Arrays;
import java.util.Random;

/**
 * The cost of chatty communication, by Monte Carlo. A page needs data for N menu items from another
 * service. Each remote call takes a log-normal latency (median 4 ms, p99 ~30 ms: a healthy in-cluster
 * call) and fails 0.1% of the time. Four ways to get the data:
 * <ul>
 *   <li><b>N+1 sequential</b>: one call per item, one after another (the ORM-style loop);</li>
 *   <li><b>N parallel</b>: one call per item, all at once: the page waits for the slowest;</li>
 *   <li><b>batch</b>: one call for all items ({@code GET /items?ids=...}), a little slower per call;</li>
 *   <li><b>local replica</b>: the data was already copied by events (chapter 19): no remote call.</li>
 * </ul>
 */
public final class ChattinessSimulation {

    public enum Strategy { SEQUENTIAL, PARALLEL, BATCH, LOCAL_REPLICA }

    public record Result(Strategy strategy, int items, double p50Ms, double p99Ms, double failedPercent,
                         int callsPerPage) {
    }

    static final double MEDIAN_MS = 4;
    static final double SIGMA = Math.log(30.0 / 4) / 2.326;          // p99 ≈ 30 ms
    static final double FAILURE = 0.001;

    public static Result run(Strategy strategy, int items, int pages, long seed) {
        Random random = new Random(seed);
        double[] latencies = new double[pages];
        int failed = 0;
        for (int p = 0; p < pages; p++) {
            double latency = 0;
            boolean ok = true;
            switch (strategy) {
                case SEQUENTIAL -> {
                    for (int i = 0; i < items; i++) {
                        latency += call(random, MEDIAN_MS);
                        ok &= random.nextDouble() >= FAILURE;
                    }
                }
                case PARALLEL -> {
                    for (int i = 0; i < items; i++) {
                        latency = Math.max(latency, call(random, MEDIAN_MS));
                        ok &= random.nextDouble() >= FAILURE;
                    }
                }
                case BATCH -> {
                    latency = call(random, MEDIAN_MS + 0.1 * items);    // bigger response, one round trip
                    ok = random.nextDouble() >= FAILURE;
                }
                case LOCAL_REPLICA -> latency = 0.05;                    // an in-memory or local DB read
            }
            latencies[p] = latency;
            if (!ok) {
                failed++;
            }
        }
        Arrays.sort(latencies);
        int calls = switch (strategy) {
            case SEQUENTIAL, PARALLEL -> items;
            case BATCH -> 1;
            case LOCAL_REPLICA -> 0;
        };
        return new Result(strategy, items, latencies[pages / 2], latencies[(int) (pages * 0.99)],
                100.0 * failed / pages, calls);
    }

    private static double call(Random random, double medianMs) {
        return medianMs * Math.exp(SIGMA * random.nextGaussian());
    }

    public static void main(String[] args) {
        System.out.println("A page needs N menu items from another service; 100,000 page loads per row.");
        System.out.println("Each call: median 4 ms, p99 ~30 ms, fails 0.1% of the time.\n");
        System.out.printf("%-15s %5s %10s %10s %12s %11s%n", "strategy", "N", "p50 ms", "p99 ms", "pages failed",
                "calls/page");
        for (int items : new int[] {12, 40}) {
            for (Strategy s : Strategy.values()) {
                Result r = run(s, items, 100_000, 20);
                System.out.printf("%-15s %5d %10.1f %10.1f %11.2f%% %11d%n", s, items, r.p50Ms(), r.p99Ms(),
                        r.failedPercent(), r.callsPerPage());
            }
        }
    }
}
