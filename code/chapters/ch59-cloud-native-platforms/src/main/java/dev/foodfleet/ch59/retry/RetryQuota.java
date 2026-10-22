package dev.foodfleet.ch59.retry;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * The SDK's "circuit breaker" token bucket: each retry costs tokens, each success puts back what its retries took (at
 * least one). When the bucket is empty, failures are returned at once instead of retried, so a client stops adding
 * retries to an overloaded or failing service. 500 tokens, 5 per retry by default.
 */
public final class RetryQuota {

    private final int max;
    private final AtomicInteger tokens;

    public RetryQuota(int max) {
        this.max = max;
        this.tokens = new AtomicInteger(max);
    }

    public boolean tryAcquire(int cost) {
        while (true) {
            int t = tokens.get();
            if (t < cost) {
                return false;
            }
            if (tokens.compareAndSet(t, t - cost)) {
                return true;
            }
        }
    }

    public void release(int amount) {
        tokens.accumulateAndGet(amount, (t, a) -> Math.min(max, t + a));
    }

    public int available() {
        return tokens.get();
    }
}
