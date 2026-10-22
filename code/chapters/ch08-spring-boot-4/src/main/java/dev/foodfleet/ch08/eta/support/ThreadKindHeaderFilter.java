package dev.foodfleet.ch08.eta.support;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Debugging aid: adds {@code X-Thread-Kind: virtual|platform} so you can see that
 * {@code spring.threads.virtual.enabled=true} took effect. Off unless
 * {@code foodfleet.debug.thread-kind-header=true}.
 */
@Component
@ConditionalOnBooleanProperty("foodfleet.debug.thread-kind-header")
class ThreadKindHeaderFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("X-Thread-Kind", Thread.currentThread().isVirtual() ? "virtual" : "platform");
        chain.doFilter(request, response);
    }
}
