package dev.foodfleet.ch42.resilience;

import java.time.Duration;
import java.util.random.RandomGenerator;

/**
 * Delay before retry number {@code attempt} (1 = the first retry). Exponential growth caps how hard a fleet of clients
 * pushes on a struggling service; <em>full jitter</em> (a uniformly random delay between zero and the exponential
 * value) spreads clients that failed at the same moment, so they don't all come back at the same moment.
 */
public record Backoff(Duration base, Duration max, boolean jitter) {

    public static Backoff fixed(Duration delay) {
        return new Backoff(delay, delay, false);
    }

    public static Backoff exponential(Duration base, Duration max) {
        return new Backoff(base, max, false);
    }

    public static Backoff fullJitter(Duration base, Duration max) {
        return new Backoff(base, max, true);
    }

    public Duration delay(int attempt, RandomGenerator random) {
        long cap = max.toNanos();
        long exp = base.toNanos() << Math.min(attempt - 1, 30);
        if (exp <= 0 || exp > cap) {
            exp = cap;   // overflowed or past the cap
        }
        return Duration.ofNanos(jitter ? (long) (random.nextDouble() * exp) : exp);
    }
}
