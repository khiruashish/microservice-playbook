package dev.foodfleet.ch42.web;

import dev.foodfleet.ch42.resilience.Deadline;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Takes the caller's deadline from {@code X-Request-Deadline} (or gives the request this service's own budget),
 * never extends it, and refuses work whose caller has already given up: by the time a request leaves Tomcat's queue
 * under overload, its caller may be long gone, and serving it anyway is how a brief overload becomes a lasting one.
 */
public final class DeadlineFilter extends OncePerRequestFilter {

    public static final String ATTRIBUTE = Deadline.class.getName();

    private final Duration ownBudget;

    public DeadlineFilter(Duration ownBudget) {
        this.ownBudget = ownBudget;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long now = System.currentTimeMillis();
        Deadline deadline = Deadline.parse(request.getHeader(Deadline.HEADER))
                .map(d -> d.min(ownBudget, now))
                .orElse(Deadline.after(ownBudget, now));
        if (deadline.expired(now)) {
            response.setStatus(503);
            response.setHeader("Retry-After", "1");
            return;
        }
        request.setAttribute(ATTRIBUTE, deadline);
        chain.doFilter(request, response);
    }
}
