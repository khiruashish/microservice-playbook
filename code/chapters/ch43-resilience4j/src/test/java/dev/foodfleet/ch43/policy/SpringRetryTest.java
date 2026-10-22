package dev.foodfleet.ch43.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.util.backoff.BackOffExecution;

/** Spring Framework 7's RetryPolicy, what @Retryable builds from its attributes. */
class SpringRetryTest {

    @Test
    void includesMatchCausesUnlikeResilience4jsExceptionLists() {
        RetryPolicy policy = RetryPolicy.builder().includes(IOException.class).build();
        assertThat(policy.shouldRetry(new UncheckedIOException(new IOException("reset")))).isTrue();
        assertThat(policy.shouldRetry(new IllegalStateException("bug"))).isFalse();
    }

    @Test
    void defaultsAreThreeRetriesOneSecondApartWithoutJitter() {
        BackOffExecution backOff = RetryPolicy.builder().build().getBackOff().start();
        assertThat(backOff.nextBackOff()).isEqualTo(1000);
        assertThat(backOff.nextBackOff()).isEqualTo(1000);
        assertThat(backOff.nextBackOff()).isEqualTo(1000);
        assertThat(backOff.nextBackOff()).isEqualTo(BackOffExecution.STOP);
    }

    @Test
    void jitterNeverGoesBelowTheInitialDelayAndGrowsWithTheMultiplier() {
        RetryPolicy policy = RetryPolicy.builder().delay(Duration.ofMillis(200)).multiplier(2).jitter(Duration.ofMillis(100)).build();
        long min1 = Long.MAX_VALUE, max1 = 0, min2 = Long.MAX_VALUE, max2 = 0;
        for (int i = 0; i < 2000; i++) {
            BackOffExecution b = policy.getBackOff().start();
            long first = b.nextBackOff();
            long second = b.nextBackOff();
            min1 = Math.min(min1, first);
            max1 = Math.max(max1, first);
            min2 = Math.min(min2, second);
            max2 = Math.max(max2, second);
        }
        assertThat(min1).isBetween(200, 210);    // 200 + 0..100: never below the initial delay
        assertThat(max1).isBetween(290, 300);
        assertThat(min2).isBetween(200, 230);    // 400 ± 200: the jitter doubled with the delay
        assertThat(max2).isBetween(570, 600);
    }
}
