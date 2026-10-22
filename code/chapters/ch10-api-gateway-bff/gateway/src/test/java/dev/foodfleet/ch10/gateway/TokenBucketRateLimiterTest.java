package dev.foodfleet.ch10.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class TokenBucketRateLimiterTest {

    private final AtomicLong nanos = new AtomicLong();
    private final TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(5, 10, 1_000, nanos::get);

    @Test
    void burstOfCapacityThenRejectsWithRetryAfter() {
        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryAcquire("1.2.3.4").allowed()).isTrue();
        }
        TokenBucketRateLimiter.Decision sixth = limiter.tryAcquire("1.2.3.4");

        assertThat(sixth.allowed()).isFalse();
        assertThat(sixth.retryAfter()).isEqualTo(Duration.ofSeconds(6));      // 10 per minute = one every 6 s
        assertThat(EdgeFilters.retryAfterSeconds(sixth.retryAfter())).isEqualTo(6);
    }

    @Test
    void tokensRefillOverTime() {
        for (int i = 0; i < 5; i++) {
            limiter.tryAcquire("1.2.3.4");
        }
        nanos.addAndGet(Duration.ofSeconds(6).toNanos());

        assertThat(limiter.tryAcquire("1.2.3.4").allowed()).isTrue();
        assertThat(limiter.tryAcquire("1.2.3.4").allowed()).isFalse();
    }

    @Test
    void clientsAreLimitedIndependently() {
        for (int i = 0; i < 5; i++) {
            limiter.tryAcquire("1.2.3.4");
        }
        assertThat(limiter.tryAcquire("5.6.7.8").allowed()).isTrue();
    }

    @Test
    void memoryIsBoundedByForgettingTheLeastRecentClient() {
        TokenBucketRateLimiter small = new TokenBucketRateLimiter(1, 1, 2, nanos::get);
        small.tryAcquire("a");
        small.tryAcquire("b");
        small.tryAcquire("c");                    // evicts "a"

        assertThat(small.tryAcquire("a").allowed()).as("a starts with a fresh bucket").isTrue();
        assertThat(small.tryAcquire("c").allowed()).isFalse();
    }
}
