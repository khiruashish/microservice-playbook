package dev.foodfleet.ch35.web;

import dev.foodfleet.ch35.identity.Caller;
import dev.foodfleet.ch35.identity.TokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates every request except login and health: a valid bearer token becomes the "caller" request
 * attribute, anything else is answered 401 before a controller runs. Deny by default: a new endpoint is protected
 * unless someone adds it to the public list. (Chapter 37 replaces this with Spring Security's filter chain.)
 */
@Component
public class TokenAuthenticationFilter extends OncePerRequestFilter {

    public static final String CALLER = "caller";
    private final TokenService tokens;

    public TokenAuthenticationFilter(TokenService tokens) {
        this.tokens = tokens;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path.equals("/login") || path.equals("/actuator/health")) {
            chain.doFilter(request, response);
            return;
        }
        String header = request.getHeader("Authorization");
        Optional<Caller> caller = header != null && header.startsWith("Bearer ")
                ? tokens.verify(header.substring(7)) : Optional.empty();
        if (caller.isEmpty()) {
            response.setStatus(401);
            response.setHeader("WWW-Authenticate", "Bearer");
            response.setContentType("application/problem+json");
            response.getWriter().write("{\"status\":401,\"title\":\"Unauthorized\"}");
            return;
        }
        request.setAttribute(CALLER, caller.get());
        chain.doFilter(request, response);
    }
}
