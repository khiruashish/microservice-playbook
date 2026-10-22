package dev.foodfleet.ch40.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class WindowLimitersTest {

    static final long S = 1_000_000_000L;

    static int burstAcrossBoundary(RateLimiter limiter) {
        // 100 requests in the last 100 ms of one minute, 100 more in the first 100 ms of the next
        int allowed = 0;
        for (int i = 0; i < 100; i++) {
            allowed += limiter.tryAcquire("c", 60 * S - S / 10 + i * S / 1000).allowed() ? 1 : 0;
        }
        for (int i = 0; i < 100; i++) {
            allowed += limiter.tryAcquire("c", 60 * S + i * S / 1000).allowed() ? 1 : 0;
        }
        return allowed;
    }

    @Test
    void fixedWindowAllowsTwiceTheLimitAroundTheBoundary() {
        assertThat(burstAcrossBoundary(new FixedWindowLimiter("orders", 100, Duration.ofMinutes(1)))).isEqualTo(200);
    }

    @Test
    void slidingWindowDoesNot() {
        assertThat(burstAcrossBoundary(new SlidingWindowLimiter("orders", 100, Duration.ofMinutes(1)))).isEqualTo(100);
    }

    @Test
    void fixedWindowResetsAtTheBoundary() {
        FixedWindowLimiter limiter = new FixedWindowLimiter("orders", 2, Duration.ofMinutes(1));
        assertThat(limiter.tryAcquire("c", 0).allowed()).isTrue();
        assertThat(limiter.tryAcquire("c", 1).allowed()).isTrue();
        RateLimiter.Decision d = limiter.tryAcquire("c", 30 * S);
        assertThat(d.allowed()).isFalse();
        assertThat(d.retryAfter()).isEqualTo(Duration.ofSeconds(30));
        assertThat(limiter.tryAcquire("c", 60 * S).allowed()).isTrue();
    }
}
