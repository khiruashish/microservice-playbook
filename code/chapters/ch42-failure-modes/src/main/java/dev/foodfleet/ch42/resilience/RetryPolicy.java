package dev.foodfleet.ch42.resilience;

import java.time.Duration;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * When to retry: at most {@code maxAttempts} in total, with a backoff, optionally within a budget, and never past the
 * caller's deadline (a retry that can't finish in time is load without a customer).
 */
public record RetryPolicy(int maxAttempts, Backoff backoff, RetryBudget budget) {

    public static RetryPolicy none() {
        return new RetryPolicy(1, Backoff.fixed(Duration.ZERO), null);
    }

    /**
     * The delay before the next attempt, or empty to give up.
     *
     * @param attemptsSoFar attempts already made (1 after the first failure)
     * @param remaining time left before the caller's deadline
     */
    public Optional<Duration> next(int attemptsSoFar, Duration remaining, RandomGenerator random) {
        if (attemptsSoFar >= maxAttempts) {
            return Optional.empty();
        }
        Duration d = backoff.delay(attemptsSoFar, random);
        if (d.compareTo(remaining) >= 0) {
            return Optional.empty();
        }
        if (budget != null && !budget.tryRetry()) {
            return Optional.empty();
        }
        return Optional.of(d);
    }
}
