package dev.foodfleet.ch43.policy;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ResilientTest {

    private final Resilient menu = Resilient.of(Retry.of("menu", ResiliencePolicies.retry()),
            CircuitBreaker.of("menu", ResiliencePolicies.circuitBreaker()), Bulkhead.of("menu", ResiliencePolicies.bulkhead(2)));
    private final AtomicInteger calls = new AtomicInteger();

    @Test
    void retriesThroughTheBreakerAndCountsEveryAttempt() {
        String result = menu.call(() -> {
            if (calls.incrementAndGet() < 3) {
                throw new IOException("reset");
            }
            return "menu";
        }, t -> "cached");
        assertThat(result).isEqualTo("menu");
        assertThat(calls.get()).isEqualTo(3);
        assertThat(menu.circuitBreaker().getMetrics().getNumberOfFailedCalls()).isEqualTo(2);
        assertThat(menu.circuitBreaker().getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(1);
    }

    @Test
    void anOpenBreakerAnswersWithTheFallbackAtOnce() {
        menu.circuitBreaker().transitionToForcedOpenState();
        long t0 = System.nanoTime();
        String result = menu.call(() -> {
            calls.incrementAndGet();
            return "menu";
        }, t -> "cached");
        assertThat(result).isEqualTo("cached");
        assertThat(calls.get()).isEqualTo(0);
        assertThat((System.nanoTime() - t0) / 1_000_000).isLessThan(50);
    }

    @Test
    void aFullBulkheadRefusesWithoutWaiting() {
        Bulkhead bulkhead = menu.bulkhead();
        assertThat(bulkhead.tryAcquirePermission()).isTrue();
        assertThat(bulkhead.tryAcquirePermission()).isTrue();
        assertThat(bulkhead.tryAcquirePermission()).isFalse();
        bulkhead.onComplete();
        bulkhead.onComplete();
    }
}
