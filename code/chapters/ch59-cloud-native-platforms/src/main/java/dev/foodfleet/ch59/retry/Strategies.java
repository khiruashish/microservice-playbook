package dev.foodfleet.ch59.retry;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/** The AWS SDK for Java v2's retry modes, with the constants of version 2.56 (its "v2.0" defaults). */
public final class Strategies {

    private Strategies() {
    }

    /** Full jitter: a random delay between 0 and min(max, base × 2^(retry - 1)). */
    static Duration fullJitter(int failedAttempt, Duration base, Duration max) {
        long cap = Math.min(max.toMillis(), base.toMillis() << Math.min(30, failedAttempt - 1));
        return Duration.ofMillis(cap <= 0 ? 0 : ThreadLocalRandom.current().nextLong(cap));
    }

    /** No retries at all: every failure goes back to the caller. */
    public static RetryStrategy none() {
        return new RetryStrategy() {
            public Optional<Duration> onFailure(int attempt, boolean throttled, double now) {
                return Optional.empty();
            }

            public void onSuccess(boolean retried, double now) {
            }

            public String name() {
                return "no retries";
            }
        };
    }

    /**
     * Legacy mode: 4 attempts; backoff from 100 ms (500 ms when throttled); retries on throttling are free, other
     * retries cost 5 tokens of a 500-token quota.
     */
    public static RetryStrategy legacy() {
        return new Quoted("legacy", 4, Duration.ofMillis(100), Duration.ofMillis(500), 5, 0);
    }

    /** Standard mode: 3 attempts; backoff from 100 ms (1 s when throttled); every retry costs 5 tokens of 500. */
    public static RetryStrategy standard() {
        return new Quoted("standard", 3, Duration.ofMillis(100), Duration.ofSeconds(1), 5, 5);
    }

    /** Adaptive mode: standard, plus a client-side rate limiter that slows down on throttling (see {@link ClientRateLimiter}). */
    public static RetryStrategy adaptive() {
        return new Adaptive();
    }

    static class Quoted implements RetryStrategy {
        final String name;
        final int maxAttempts;
        final Duration base;
        final Duration throttledBase;
        final int cost;
        final int throttledCost;
        final RetryQuota quota = new RetryQuota(500);
        private final ThreadLocal<Integer> acquired = ThreadLocal.withInitial(() -> 0);

        Quoted(String name, int maxAttempts, Duration base, Duration throttledBase, int cost, int throttledCost) {
            this.name = name;
            this.maxAttempts = maxAttempts;
            this.base = base;
            this.throttledBase = throttledBase;
            this.cost = cost;
            this.throttledCost = throttledCost;
        }

        public Optional<Duration> onFailure(int attempt, boolean throttled, double now) {
            if (attempt >= maxAttempts) {
                acquired.set(0);
                return Optional.empty();
            }
            int c = throttled ? throttledCost : cost;
            if (!quota.tryAcquire(c)) {
                acquired.set(0);
                return Optional.empty();                      // quota exhausted: fail fast
            }
            acquired.set(acquired.get() + c);
            return Optional.of(fullJitter(attempt, throttled ? throttledBase : base, Duration.ofSeconds(20)));
        }

        public void onSuccess(boolean retried, double now) {
            quota.release(Math.max(acquired.get(), 1));
            acquired.set(0);
        }

        public String name() {
            return name;
        }
    }

    static final class Adaptive extends Quoted {
        final ClientRateLimiter limiter = new ClientRateLimiter();

        Adaptive() {
            super("adaptive", 3, Duration.ofMillis(100), Duration.ofSeconds(1), 5, 5);
        }

        @Override
        public Duration beforeAttempt(double now) {
            return limiter.acquire(now);
        }

        @Override
        public Optional<Duration> onFailure(int attempt, boolean throttled, double now) {
            limiter.update(now, throttled);
            return super.onFailure(attempt, throttled, now);
        }

        @Override
        public void onSuccess(boolean retried, double now) {
            limiter.update(now, false);
            super.onSuccess(retried, now);
        }
    }
}
