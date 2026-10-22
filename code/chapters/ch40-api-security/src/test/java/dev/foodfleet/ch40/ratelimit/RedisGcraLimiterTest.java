package dev.foodfleet.ch40.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The Java side of the Redis limiter; the script itself runs against a real Redis in verify/run.sh. */
class RedisGcraLimiterTest {

    @Test
    void passesTheIntervalAndToleranceInMicrosecondsAndAHashTaggedKey() {
        List<List<String>> calls = new ArrayList<>();
        RedisGcraLimiter limiter = new RedisGcraLimiter("client", 120, Duration.ofMinutes(1), 20, (script, keys, args) -> {
            calls.add(keys);
            calls.add(args);
            return List.of(1L, 0L, 500_000L);
        });
        RateLimiter.Decision d = limiter.tryAcquire("foodfleet-in/app:cust-42", 0);
        assertThat(calls.get(0)).containsExactly("rl:client:{foodfleet-in/app:cust-42}");
        assertThat(calls.get(1)).containsExactly("500000", "9500000");    // 0.5 s apart, 19 more at once
        assertThat(d.allowed()).isTrue();
        assertThat(d.remaining()).isEqualTo(19);
    }

    @Test
    void aDenialCarriesRetryAfter() {
        RedisGcraLimiter limiter = new RedisGcraLimiter("client", 120, Duration.ofMinutes(1), 20,
                (script, keys, args) -> List.of(0L, 250_000L, 10_000_000L));
        RateLimiter.Decision d = limiter.tryAcquire("k", 0);
        assertThat(d.allowed()).isFalse();
        assertThat(d.retryAfter()).isEqualTo(Duration.ofMillis(250));
    }

    @Test
    void redisFailuresAreReportedNotTreatedAsAllowed() {
        RedisGcraLimiter limiter = new RedisGcraLimiter("client", 120, Duration.ofMinutes(1), 20, (script, keys, args) -> {
            throw new java.net.SocketTimeoutException("50 ms");
        });
        assertThatThrownBy(() -> limiter.tryAcquire("k", 0)).isInstanceOf(RedisGcraLimiter.Unavailable.class);
    }

    @Test
    void theScriptIsOnTheClasspath() {
        assertThat(RedisGcraLimiter.SCRIPT).contains("redis.call('TIME')");
    }
}
