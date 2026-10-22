package dev.foodfleet.ch40.ratelimit;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The response headers that tell a client its quota, as in the IETF HTTPAPI draft "RateLimit header fields for
 * HTTP" (draft-ietf-httpapi-ratelimit-headers-11, May 2026; structured fields):
 * <pre>
 * RateLimit-Policy: "orders";q=100;w=60
 * RateLimit: "orders";r=37;t=21
 * Retry-After: 4              (on 429 only; takes precedence over t)
 * </pre>
 * Still a draft: many APIs send the older {@code X-RateLimit-Limit/-Remaining/-Reset} instead. Whatever the names,
 * a client that is told when to come back retries less, and less often in lockstep.
 */
public final class RateLimitHeaders {

    private RateLimitHeaders() {
    }

    public static Map<String, String> of(RateLimiter.Policy policy, RateLimiter.Decision decision) {
        Map<String, String> h = new LinkedHashMap<>();
        String name = "\"" + policy.name().replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        h.put("RateLimit-Policy", name + ";q=" + policy.quota() + ";w=" + policy.window().toSeconds());
        h.put("RateLimit", name + ";r=" + decision.remaining() + ";t=" + secondsUp(decision.resetIn()));
        if (!decision.allowed()) {
            // delay-seconds, rounded up: retrying a little late is fine, a little early is another 429
            h.put("Retry-After", Long.toString(Math.max(1, secondsUp(decision.retryAfter()))));
        }
        return h;
    }

    private static long secondsUp(Duration d) {
        return (d.toNanos() + 999_999_999L) / 1_000_000_000L;
    }
}
