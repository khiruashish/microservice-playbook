package dev.foodfleet.ch40.ratelimit;

import java.time.Duration;

/**
 * Decides whether one more request from {@code key} (a customer, an API client, an IP address) may proceed now.
 * Implementations take the time as an argument so tests and measurements control it.
 */
public interface RateLimiter {

    /** The outcome, with what a client needs to back off politely. */
    record Decision(boolean allowed, long remaining, Duration retryAfter, Duration resetIn) {

        public static Decision allow(long remaining, Duration resetIn) {
            return new Decision(true, remaining, Duration.ZERO, resetIn);
        }

        public static Decision deny(Duration retryAfter, Duration resetIn) {
            return new Decision(false, 0, retryAfter, resetIn);
        }
    }

    /** The quota a client is told about: {@code quota} requests per {@code window}. */
    record Policy(String name, int quota, Duration window) {
    }

    Decision tryAcquire(String key, long nowNanos);

    Policy policy();
}
