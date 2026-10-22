package dev.foodfleet.ch43.policy;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class CircuitBreakerPolicyTest {

    private final CircuitBreaker breaker = CircuitBreaker.of("restaurant-service", ResiliencePolicies.circuitBreaker());

    private void fail(Throwable t) {
        breaker.acquirePermission();
        breaker.onError(5, TimeUnit.MILLISECONDS, t);
    }

    private void succeed(long millis) {
        breaker.acquirePermission();
        breaker.onSuccess(millis, TimeUnit.MILLISECONDS);
    }

    @Test
    void staysClosedUntilTwentyCallsHaveBeenSeen() {
        for (int i = 0; i < 19; i++) {
            fail(new IOException("down"));
        }
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        fail(new IOException("down"));
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    void opensWhenHalfOfTheCallsAreSlowEvenWithoutErrors() {
        for (int i = 0; i < 10; i++) {
            succeed(20);
        }
        for (int i = 0; i < 10; i++) {
            succeed(2_000);
        }
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    void clientErrorsSayNothingAboutTheDependency() {
        for (int i = 0; i < 50; i++) {
            fail(new ClientErrorException(404, "no such restaurant"));
        }
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(breaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(0);
    }
}
