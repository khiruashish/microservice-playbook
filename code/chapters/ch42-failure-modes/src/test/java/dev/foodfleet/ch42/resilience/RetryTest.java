package dev.foodfleet.ch42.resilience;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Random;
import org.junit.jupiter.api.Test;

class RetryTest {

    @Test
    void exponentialBackoffGrowsAndIsCapped() {
        Backoff b = Backoff.exponential(Duration.ofMillis(100), Duration.ofSeconds(1));
        Random r = new Random(1);
        assertThat(b.delay(1, r)).isEqualTo(Duration.ofMillis(100));
        assertThat(b.delay(2, r)).isEqualTo(Duration.ofMillis(200));
        assertThat(b.delay(4, r)).isEqualTo(Duration.ofMillis(800));
        assertThat(b.delay(5, r)).isEqualTo(Duration.ofSeconds(1));
        assertThat(b.delay(60, r)).isEqualTo(Duration.ofSeconds(1));          // no overflow
        assertThat(Backoff.fixed(Duration.ofSeconds(1)).delay(7, r)).isEqualTo(Duration.ofSeconds(1));
    }

    @Test
    void fullJitterSpreadsBetweenZeroAndTheExponentialValue() {
        Backoff b = Backoff.fullJitter(Duration.ofMillis(100), Duration.ofSeconds(1));
        Random r = new Random(7);
        long min = Long.MAX_VALUE, max = 0;
        for (int i = 0; i < 1000; i++) {
            long d = b.delay(3, r).toMillis();
            min = Math.min(min, d);
            max = Math.max(max, d);
        }
        assertThat(min).isLessThan(20);
        assertThat(max).isBetween(380, 400);
    }

    @Test
    void theBudgetAllowsRetriesInProportionToTraffic() {
        RetryBudget budget = new RetryBudget(0.1, 10);
        int allowed = 0;
        for (int i = 0; i < 1000; i++) {                 // 1,000 requests, every one failing and wanting a retry
            budget.onRequest();
            allowed += budget.tryRetry() ? 1 : 0;
        }
        assertThat(allowed).isBetween(100, 115);           // 10% plus the initial balance
        assertThat(budget.tryRetry()).isFalse();
    }

    @Test
    void thePolicyStopsAtMaxAttemptsTheDeadlineOrAnEmptyBudget() {
        RetryPolicy p = new RetryPolicy(3, Backoff.fixed(Duration.ofMillis(100)), null);
        Random r = new Random(1);
        assertThat(p.next(1, Duration.ofSeconds(1), r)).isPresent();
        assertThat(p.next(2, Duration.ofSeconds(1), r)).isPresent();
        assertThat(p.next(3, Duration.ofSeconds(1), r)).isEmpty();
        assertThat(p.next(1, Duration.ofMillis(50), r)).isEmpty();          // the wait wouldn't fit before the deadline
        RetryPolicy budgeted = new RetryPolicy(3, Backoff.fixed(Duration.ZERO), new RetryBudget(0.1, 1));
        assertThat(budgeted.next(1, Duration.ofSeconds(1), r)).isPresent();
        assertThat(budgeted.next(1, Duration.ofSeconds(1), r)).isEmpty();
        assertThat(RetryPolicy.none().next(1, Duration.ofSeconds(1), r)).isEmpty();
    }
}
