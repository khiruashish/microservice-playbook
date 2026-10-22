package dev.foodfleet.ch17.ambassador;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class CircuitBreakerTest {

    private final AtomicLong now = new AtomicLong();
    private final CircuitBreaker breaker = new CircuitBreaker(3, Duration.ofSeconds(10), now::get);

    @Test
    void opensAfterConsecutiveFailures() {
        breaker.failure();
        breaker.failure();
        assertThat(breaker.allow()).isTrue();
        breaker.failure();

        assertThat(breaker.allow()).isFalse();
        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    void aSuccessResetsTheCount() {
        breaker.failure();
        breaker.failure();
        breaker.success();
        breaker.failure();

        assertThat(breaker.allow()).isTrue();
    }

    @Test
    void afterTheOpenPeriodOneTrialIsAllowed() {
        for (int i = 0; i < 3; i++) {
            breaker.failure();
        }
        now.addAndGet(Duration.ofSeconds(10).toNanos());

        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
        assertThat(breaker.allow()).isTrue();          // the trial
        assertThat(breaker.allow()).isFalse();         // nobody else while it's in flight
        breaker.success();
        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void aFailedTrialOpensAgain() {
        for (int i = 0; i < 3; i++) {
            breaker.failure();
        }
        now.addAndGet(Duration.ofSeconds(10).toNanos());
        breaker.allow();
        breaker.failure();

        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.OPEN);
    }
}
