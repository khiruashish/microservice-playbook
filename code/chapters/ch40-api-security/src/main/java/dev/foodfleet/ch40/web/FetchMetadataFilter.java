package dev.foodfleet.ch40.web;

import dev.foodfleet.ch40.csrf.FetchMetadataPolicy;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/** Refuses requests to /bff that another site started, using the headers the browser sets (FetchMetadataPolicy). */
public final class FetchMetadataFilter extends OncePerRequestFilter {

    private final FetchMetadataPolicy policy;

    public FetchMetadataFilter(FetchMetadataPolicy policy) {
        this.policy = policy;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        FetchMetadataPolicy.Verdict v = policy.check(request.getMethod(), request.getHeader("Sec-Fetch-Site"),
                request.getHeader("Sec-Fetch-Mode"), request.getHeader("Sec-Fetch-Dest"), request.getHeader("Origin"));
        response.addHeader("Vary", "Sec-Fetch-Site, Sec-Fetch-Mode, Sec-Fetch-Dest");
        if (!v.allowed()) {
            response.setStatus(403);
            return;
        }
        chain.doFilter(request, response);
    }
}
