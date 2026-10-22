package dev.foodfleet.ch11.simulation;

import java.util.Arrays;
import java.util.SplittableRandom;

/**
 * Monte Carlo over the timer phases: how long does traffic keep going to a dead instance, and what
 * does that cost? Deterministic for a given seed, so the chapter's numbers are reproducible.
 */
public final class FailoverSimulation {

    /**
     * @param totalRps  requests per second to the service
     * @param instances instances behind it (one of them dies); traffic is spread evenly
     */
    public record Scenario(double totalRps, int instances, int trials, long seed) {
        public double rpsToDeadInstance() {
            return totalRps / instances;
        }
    }

    /**
     * @param failedRequests  expected requests that fail (no retry elsewhere)
     * @param slowedRequests  expected requests that succeed only after a retry on another instance
     */
    public record Result(String mechanism, double meanSeconds, double p50Seconds, double p99Seconds,
                         double failedRequests, double slowedRequests) {
    }

    public Result run(Mechanism mechanism, Scenario scenario) {
        SplittableRandom random = new SplittableRandom(scenario.seed());
        double[] windows = new double[scenario.trials()];
        for (int t = 0; t < windows.length; t++) {
            double window = 0;
            for (Stage stage : mechanism.stages()) {
                window += stage.sampleSeconds(random);
            }
            windows[t] = window;
        }
        Arrays.sort(windows);
        double mean = Arrays.stream(windows).average().orElse(0);
        double affected = scenario.rpsToDeadInstance() * mean;
        return new Result(mechanism.name(), mean, percentile(windows, 0.50), percentile(windows, 0.99),
                mechanism.retriesOnAnotherInstance() ? 0 : affected,
                mechanism.retriesOnAnotherInstance() ? affected : 0);
    }

    static double percentile(double[] sorted, double q) {
        int index = (int) Math.ceil(q * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(sorted.length - 1, index))];
    }
}
