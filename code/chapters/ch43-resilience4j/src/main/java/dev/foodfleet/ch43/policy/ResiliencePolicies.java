package dev.foodfleet.ch43.policy;

import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import java.time.Duration;

/**
 * FoodFleet's defaults for a synchronous call to another service, replacing Resilience4j's library defaults (which
 * are tuned for nothing in particular: a 100-call window, a 60 s open state, 60 s before a call counts as slow).
 * Each value is explained in chapter 43; the same values are in {@code order-service.yml} for the annotations.
 */
public final class ResiliencePolicies {

    private ResiliencePolicies() {
    }

    public static CircuitBreakerConfig circuitBreaker() {
        return CircuitBreakerConfig.custom()
                // judge the last 5 seconds, not the last 100 calls: a quiet endpoint reacts as fast as a busy one,
                // and 20 calls at least, so a few unlucky calls on a quiet endpoint don't open it
                .slidingWindow(5, 20, SlidingWindowType.TIME_BASED)
                .failureRateThreshold(50)
                // a dependency that answers in 2 s is down for our callers, even if it never errors
                .slowCallDurationThreshold(Duration.ofSeconds(1))
                .slowCallRateThreshold(50)
                // probe with 5 calls every 5 s: probing is cheap, staying open after the dependency recovers is not
                .waitDurationInOpenState(Duration.ofSeconds(5))
                .permittedNumberOfCallsInHalfOpenState(5)
                // 4xx is our fault, not the dependency's
                .ignoreExceptions(ClientErrorException.class)
                .build();
    }

    public static RetryConfig retry() {
        return RetryConfig.custom()
                .maxAttempts(3)                                        // the first call and two retries
                .intervalFunction(IntervalFunction.ofExponentialRandomBackoff(Duration.ofMillis(100), 2, 1.0, Duration.ofSeconds(1)))
                .retryOnException(Transient::failure)                 // I/O or timeout anywhere in the cause chain
                // never retried: an open breaker, a full bulkhead or a rate limit is an answer, not a failure, and
                // a 4xx won't change. One call: each ignoreExceptions(...) on the builder replaces the previous one
                .ignoreExceptions(CallNotPermittedException.class, BulkheadFullException.class,
                        RequestNotPermitted.class, ClientErrorException.class)
                .build();
    }

    public static BulkheadConfig bulkhead(int maxConcurrentCalls) {
        return BulkheadConfig.custom()
                .maxConcurrentCalls(maxConcurrentCalls)
                .maxWaitDuration(Duration.ZERO)                        // full means full: fail fast
                .build();
    }

    public static TimeLimiterConfig timeLimiter(Duration timeout) {
        return TimeLimiterConfig.custom()
                .timeoutDuration(timeout)
                .cancelRunningFuture(true)
                .build();
    }
}
