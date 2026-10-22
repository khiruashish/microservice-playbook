package dev.foodfleet.lab14.mvc;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Virtual threads remove the thread pool, and with it the only limit on how much work is in progress. Past capacity
 * the unbounded server queued requests until they timed out (chapter 66: 190-577 served/s of 2,500 offered, p99 up to
 * 6.5 s); with at most 300 in progress and a 503 beyond, it kept serving 1,510-1,588/s with a p99 of 261-303 ms.
 */
@Component
class ConcurrencyLimit extends OncePerRequestFilter {

    private final Semaphore permits;

    ConcurrencyLimit(@Value("${foodfleet.max-in-progress:300}") int maxInProgress) {
        this.permits = new Semaphore(maxInProgress);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!permits.tryAcquire()) {
            response.setStatus(503);
            response.setHeader("Retry-After", "1");
            return;
        }
        try {
            chain.doFilter(request, response);
        } finally {
            permits.release();
        }
    }
}
