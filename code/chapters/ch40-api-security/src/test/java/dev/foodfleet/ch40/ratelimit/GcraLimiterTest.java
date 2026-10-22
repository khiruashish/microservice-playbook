package dev.foodfleet.ch40.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class GcraLimiterTest {

    static final long S = 1_000_000_000L;

    @Test
    void allowsTheBurstAtOnceThenOneRequestPerInterval() {
        // 60 a minute = one a second, up to 5 back to back
        GcraLimiter limiter = new GcraLimiter("orders", 60, Duration.ofMinutes(1), 5);
        long t = 1_000 * S;
        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryAcquire("c1", t).allowed()).isTrue();
        }
        RateLimiter.Decision denied = limiter.tryAcquire("c1", t);
        assertThat(denied.allowed()).isFalse();
        assertThat(denied.retryAfter()).isEqualTo(Duration.ofSeconds(1));
        assertThat(limiter.tryAcquire("c1", t + S - 1).allowed()).isFalse();
        assertThat(limiter.tryAcquire("c1", t + S).allowed()).isTrue();
        assertThat(limiter.tryAcquire("c2", t).allowed()).isTrue();          // keys are independent
    }

    @Test
    void remainingCountsDownAndRefills() {
        GcraLimiter limiter = new GcraLimiter("orders", 60, Duration.ofMinutes(1), 5);
        long t = 0;
        assertThat(limiter.tryAcquire("c", t).remaining()).isEqualTo(4);
        assertThat(limiter.tryAcquire("c", t).remaining()).isEqualTo(3);
        assertThat(limiter.tryAcquire("c", t + 10 * S).remaining()).isEqualTo(4);   // full again, minus this one
    }

    @Test
    void neverMoreThanLimitPlusBurstInAnyWindow() {
        GcraLimiter limiter = new GcraLimiter("orders", 100, Duration.ofMinutes(1), 10);
        int allowed = 0;
        for (long t = 0; t < 60 * S; t += S / 100) {                          // 100 attempts a second for a minute
            if (limiter.tryAcquire("c", t).allowed()) {
                allowed++;
            }
        }
        assertThat(allowed).isBetween(100, 110);
    }

    @Test
    void idleKeysAreForgotten() {
        GcraLimiter limiter = new GcraLimiter("orders", 60, Duration.ofMinutes(1), 5);
        limiter.tryAcquire("a", 0);
        limiter.tryAcquire("b", 0);
        assertThat(limiter.evictIdle(S / 2)).isEqualTo(0);
        assertThat(limiter.evictIdle(2 * S)).isEqualTo(2);
        assertThat(limiter.size()).isZero();
    }
}
