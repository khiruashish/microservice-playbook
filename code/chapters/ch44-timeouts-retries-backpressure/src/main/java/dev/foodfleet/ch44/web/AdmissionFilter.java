package dev.foodfleet.ch44.web;

import com.netflix.concurrency.limits.Limiter;
import dev.foodfleet.ch44.limit.Priority;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Admission control at the front of order-service: every request needs a permit from the adaptive, partitioned
 * limiter, or gets a 503 at once (cheap, honest, retryable by the edge within its budget). The limiter learns from
 * outcomes: a 5xx or a slow answer shrinks the limit, a 4xx says nothing about our capacity.
 */
public class AdmissionFilter extends OncePerRequestFilter {

    private final Limiter<Priority> limiter;

    public AdmissionFilter(Limiter<Priority> limiter) {
        this.limiter = limiter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<Limiter.Listener> permit = limiter.acquire(Priority.of(request.getHeader(Priority.HEADER)));
        if (permit.isEmpty()) {
            response.setStatus(503);
            response.setHeader("Retry-After", "1");
            return;
        }
        Limiter.Listener listener = permit.get();
        try {
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException e) {
            listener.onDropped();
            throw e;
        }
        int status = response.getStatus();
        if (status >= 500) {
            listener.onDropped();
        } else if (status >= 400) {
            listener.onIgnore();
        } else {
            listener.onSuccess();
        }
    }
}
