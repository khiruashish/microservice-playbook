package dev.foodfleet.ch59.retry;

import java.time.Duration;
import java.util.Optional;

/**
 * How a cloud SDK client retries, reduced to the decisions the AWS SDK for Java v2 (2.56) makes: whether to send an
 * attempt now, whether to retry a failure and after how long, and what a success gives back. One instance per SDK
 * client, shared by all its threads, as in the SDK.
 */
public interface RetryStrategy {

    /** Before every attempt: how long to wait first (only the adaptive strategy ever waits). */
    default Duration beforeAttempt(double nowSeconds) {
        return Duration.ZERO;
    }

    /**
     * After a failed attempt number {@code attempt} (1 for the first): the backoff before the next attempt, or empty to
     * give up and return the error to the caller.
     */
    Optional<Duration> onFailure(int attempt, boolean throttled, double nowSeconds);

    /** After a successful attempt; {@code retried}: whether this call needed retries. */
    void onSuccess(boolean retried, double nowSeconds);

    String name();
}
