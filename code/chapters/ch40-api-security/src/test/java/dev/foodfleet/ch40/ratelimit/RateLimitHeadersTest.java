package dev.foodfleet.ch40.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RateLimitHeadersTest {

    @Test
    void allowedResponsesCarryPolicyAndRemaining() {
        Map<String, String> h = RateLimitHeaders.of(new RateLimiter.Policy("orders", 100, Duration.ofMinutes(1)),
                RateLimiter.Decision.allow(37, Duration.ofMillis(20_400)));
        assertThat(h.get("RateLimit-Policy")).isEqualTo("\"orders\";q=100;w=60");
        assertThat(h.get("RateLimit")).isEqualTo("\"orders\";r=37;t=21");
        assertThat(h.containsKey("Retry-After")).isFalse();
    }

    @Test
    void deniedResponsesSayWhenToComeBackRoundedUp() {
        Map<String, String> h = RateLimitHeaders.of(new RateLimiter.Policy("orders", 100, Duration.ofMinutes(1)),
                RateLimiter.Decision.deny(Duration.ofMillis(3_100), Duration.ofSeconds(9)));
        assertThat(h.get("Retry-After")).isEqualTo("4");
        assertThat(h.get("RateLimit")).isEqualTo("\"orders\";r=0;t=9");
        assertThat(RateLimitHeaders.of(new RateLimiter.Policy("o", 1, Duration.ofSeconds(1)),
                RateLimiter.Decision.deny(Duration.ofMillis(20), Duration.ofMillis(20))).get("Retry-After")).isEqualTo("1");
    }
}
