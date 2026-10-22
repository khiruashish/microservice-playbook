package dev.foodfleet.ch45.chaos;

/**
 * The steady state an experiment expects to keep: in each window, at least {@code minSuccessRate} of requests
 * succeed and the p99 stays at or under {@code maxP99Millis}. Stated in what users see (the SLO), not in what the
 * system does internally, so it stays true whatever the implementation.
 */
public record Hypothesis(String statement, double minSuccessRate, long maxP99Millis, long minRequests) {

    public boolean holds(Window w) {
        if (w.requests() < minRequests) {
            return true;   // too little traffic to judge
        }
        return w.successRate() >= minSuccessRate && w.p99Millis() <= maxP99Millis;
    }
}
