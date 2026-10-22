package dev.foodfleet.ch40.web;

import dev.foodfleet.ch40.ratelimit.GcraLimiter;
import dev.foodfleet.ch40.ratelimit.LayeredLimiter;
import dev.foodfleet.ch40.ratelimit.LayeredLimiter.Caller;
import dev.foodfleet.ch40.ratelimit.LayeredLimiter.Outcome;
import dev.foodfleet.ch40.ratelimit.RateLimitHeaders;
import dev.foodfleet.ch40.ratelimit.RedisGcraLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rate limits for /api, after authentication so the key is who is calling, not where from: per OAuth client or
 * user within a tenant, then per tenant, both from claims in the verified token. Anonymous calls (the public
 * restaurant search) are limited per address, as seen by this server: behind a load balancer that is
 * {@code server.forward-headers-strategy=native} with the balancer's address trusted, never a raw
 * X-Forwarded-For a client can write.
 * <p>
 * Over the limit: 429 with Retry-After and the RateLimit fields, before any business code runs. Redis down: the
 * per-instance fallback limits, and a counter to alert on.
 */
public final class RateLimitFilter extends OncePerRequestFilter {

    private final LayeredLimiter shared;
    private final LayeredLimiter fallback;
    private final GcraLimiter anonymous;
    private final AtomicLong fallbackDecisions = new AtomicLong();

    public RateLimitFilter(LayeredLimiter shared, LayeredLimiter fallback, GcraLimiter anonymous) {
        this.shared = shared;
        this.fallback = fallback;
        this.anonymous = anonymous;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long now = System.nanoTime();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Outcome outcome;
        if (auth instanceof JwtAuthenticationToken jwt) {
            String tenant = jwt.getToken().getClaimAsString("tenant");
            String client = jwt.getToken().getClaimAsString("azp");
            Caller caller = new Caller(tenant == null ? "-" : tenant,
                    (client == null ? "-" : client) + ":" + jwt.getName(), request.getRemoteAddr());
            try {
                outcome = shared.tryAcquire(caller, now);
            } catch (RedisGcraLimiter.Unavailable e) {
                fallbackDecisions.incrementAndGet();
                outcome = fallback.tryAcquire(caller, now);
            }
        } else {
            outcome = new Outcome(anonymous.tryAcquire(request.getRemoteAddr(), now), anonymous.policy());
        }
        RateLimitHeaders.of(outcome.policy(), outcome.decision()).forEach(response::setHeader);
        if (!outcome.decision().allowed()) {
            response.setStatus(429);
            response.setContentType("application/problem+json");
            response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Too Many Requests\",\"status\":429}");
            return;
        }
        chain.doFilter(request, response);
    }

    /** Exported as a gauge: decisions made without Redis since start. Non-zero means limits are approximate. */
    public long fallbackDecisions() {
        return fallbackDecisions.get();
    }
}
