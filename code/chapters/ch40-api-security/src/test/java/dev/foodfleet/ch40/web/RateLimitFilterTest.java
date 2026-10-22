package dev.foodfleet.ch40.web;

import static org.assertj.core.api.Assertions.assertThat;

import dev.foodfleet.ch40.ratelimit.GcraLimiter;
import dev.foodfleet.ch40.ratelimit.LayeredLimiter;
import dev.foodfleet.ch40.ratelimit.LayeredLimiter.Layer;
import dev.foodfleet.ch40.ratelimit.RedisGcraLimiter;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class RateLimitFilterTest {

    static LayeredLimiter layered(dev.foodfleet.ch40.ratelimit.RateLimiter client) {
        return new LayeredLimiter(List.of(new Layer(client, c -> c.tenant() + "/" + c.client())));
    }

    final LayeredLimiter redisDown = layered(new RedisGcraLimiter("client", 120, Duration.ofMinutes(1), 2,
            (s, k, a) -> {
                throw new java.net.ConnectException("connection refused");
            }));
    final LayeredLimiter fallback = layered(new GcraLimiter("client", 20, Duration.ofMinutes(1), 1));
    final GcraLimiter anonymous = new GcraLimiter("anonymous", 30, Duration.ofMinutes(1), 2);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    MockHttpServletResponse call(RateLimitFilter filter) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/restaurants");
        req.setRemoteAddr("203.0.113.7");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());
        return res;
    }

    @Test
    void anonymousCallersAreLimitedPerAddressWith429AndRetryAfter() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(redisDown, fallback, anonymous);
        assertThat(call(filter).getStatus()).isEqualTo(200);
        MockHttpServletResponse second = call(filter);
        assertThat(second.getStatus()).isEqualTo(200);
        assertThat(second.getHeader("RateLimit")).isEqualTo("\"anonymous\";r=0;t=4");
        MockHttpServletResponse third = call(filter);
        assertThat(third.getStatus()).isEqualTo(429);
        assertThat(third.getHeader("Retry-After")).isEqualTo("2");
        assertThat(third.getContentType()).isEqualTo("application/problem+json");
    }

    @Test
    void authenticatedCallersFallBackToLocalLimitsWhenRedisIsDown() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(new Jwt(Map.of(
                "sub", "cust-42", "tenant", "foodfleet-in", "azp", "android-app"))));
        RateLimitFilter filter = new RateLimitFilter(redisDown, fallback, anonymous);
        assertThat(call(filter).getStatus()).isEqualTo(200);
        assertThat(call(filter).getStatus()).isEqualTo(429);          // the fallback allows a burst of 1
        assertThat(filter.fallbackDecisions()).isEqualTo(2);
    }
}
