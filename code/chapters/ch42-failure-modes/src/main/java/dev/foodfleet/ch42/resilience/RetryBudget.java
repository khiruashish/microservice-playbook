package dev.foodfleet.ch42.resilience;

/**
 * Retries limited to a fraction of the traffic that is succeeding: each request deposits {@code ratio} tokens, each
 * retry withdraws one, and the balance is capped. While a dependency is healthy a few retries ride over blips; when it
 * fails outright, the budget runs dry and the client stops multiplying the load (gRPC's retry throttling and
 * Finagle's RetryBudget work the same way).
 */
public final class RetryBudget {

    private final double ratio;
    private final double cap;
    private double tokens;

    /**
     * @param ratio retries allowed per request, e.g. 0.1 for "at most 10% extra load"
     * @param maxBalance the most retries that can be saved up and spent at once
     */
    public RetryBudget(double ratio, int maxBalance) {
        this.ratio = ratio;
        this.cap = Math.max(maxBalance, 1);
        this.tokens = cap;
    }

    /** Call once per original request (not per retry). */
    public synchronized void onRequest() {
        tokens = Math.min(cap, tokens + ratio);
    }

    /** True if a retry may be sent now; withdraws a token. */
    public synchronized boolean tryRetry() {
        if (tokens >= 1) {
            tokens -= 1;
            return true;
        }
        return false;
    }

    public synchronized double balance() {
        return tokens;
    }
}
