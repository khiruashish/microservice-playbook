package dev.foodfleet.ch43.policy;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.functions.CheckedSupplier;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import java.util.function.Function;

/**
 * One dependency's resilience pipeline, composed in code in the same order Spring Boot's Resilience4j aspects apply
 * by default: {@code Retry ( CircuitBreaker ( RateLimiter ( TimeLimiter ( Bulkhead ( call ) ) ) ) )}. Retry is
 * outermost, so every attempt is counted by the breaker and every attempt needs a bulkhead permit.
 */
public final class Resilient {

    private final Retry retry;
    private final CircuitBreaker circuitBreaker;
    private final Bulkhead bulkhead;

    private Resilient(Retry retry, CircuitBreaker circuitBreaker, Bulkhead bulkhead) {
        this.retry = retry;
        this.circuitBreaker = circuitBreaker;
        this.bulkhead = bulkhead;
    }

    /** The named instances from FoodFleet's registries (configured with {@link ResiliencePolicies}). */
    public static Resilient of(String dependency, RetryRegistry retries, CircuitBreakerRegistry breakers, BulkheadRegistry bulkheads) {
        return new Resilient(retries.retry(dependency), breakers.circuitBreaker(dependency), bulkheads.bulkhead(dependency));
    }

    public static Resilient of(Retry retry, CircuitBreaker circuitBreaker, Bulkhead bulkhead) {
        return new Resilient(retry, circuitBreaker, bulkhead);
    }

    /** Runs the call through bulkhead, breaker and retry, innermost first. */
    public <T> T call(CheckedSupplier<T> call) throws Throwable {
        CheckedSupplier<T> limited = Bulkhead.decorateCheckedSupplier(bulkhead, call);
        CheckedSupplier<T> guarded = CircuitBreaker.decorateCheckedSupplier(circuitBreaker, limited);
        return Retry.decorateCheckedSupplier(retry, guarded).get();
    }

    /** As {@link #call}, answering with {@code fallback} when the pipeline gives up or refuses. */
    public <T> T call(CheckedSupplier<T> call, Function<Throwable, T> fallback) {
        try {
            return call(call);
        } catch (Error e) {
            throw e;
        } catch (Throwable t) {
            return fallback.apply(t);
        }
    }

    public CircuitBreaker circuitBreaker() {
        return circuitBreaker;
    }

    public Retry retry() {
        return retry;
    }

    public Bulkhead bulkhead() {
        return bulkhead;
    }
}
