package dev.foodfleet.ch43.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RetryPolicyTest {

    private final Retry retry = Retry.of("restaurant-service", ResiliencePolicies.retry());
    private final AtomicInteger calls = new AtomicInteger();

    private void callThrowing(RuntimeException e) {
        Retry.decorateSupplier(retry, () -> {
            calls.incrementAndGet();
            throw e;
        }).get();
    }

    @Test
    void retriesAnIoErrorWrappedTheWayRestClientWrapsIt() {
        assertThatThrownBy(() -> callThrowing(new UncheckedIOException(new IOException("connection reset"))))
                .isInstanceOf(UncheckedIOException.class);
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void doesNotRetryAnOpenBreaker() {
        CircuitBreaker open = CircuitBreaker.ofDefaults("open");
        open.transitionToForcedOpenState();
        assertThatThrownBy(() -> callThrowing(CallNotPermittedException.createCallNotPermittedException(open)))
                .isInstanceOf(CallNotPermittedException.class);
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void doesNotRetryClientErrors() {
        assertThatThrownBy(() -> callThrowing(new ClientErrorException(400, "bad request"))).isInstanceOf(ClientErrorException.class);
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void doesNotRetryProgrammingErrors() {
        assertThatThrownBy(() -> callThrowing(new IllegalStateException("bug"))).isInstanceOf(IllegalStateException.class);
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void ignoreExceptionsCalledTwiceKeepsOnlyTheLastList() {
        RetryConfig config = RetryConfig.custom()
                .ignoreExceptions(IllegalStateException.class)
                .ignoreExceptions(IllegalArgumentException.class)
                .build();
        assertThat(config.getExceptionPredicate().test(new IllegalStateException())).isTrue();      // retried after all
        assertThat(config.getExceptionPredicate().test(new IllegalArgumentException())).isFalse();
    }

    @Test
    void transientLooksThroughTheCauseChain() {
        assertThat(Transient.failure(new RuntimeException(new RuntimeException(new IOException())))).isTrue();
        assertThat(Transient.failure(new RuntimeException("no cause"))).isFalse();
    }
}
