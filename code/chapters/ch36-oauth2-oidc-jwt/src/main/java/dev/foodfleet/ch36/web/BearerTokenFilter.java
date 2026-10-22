package dev.foodfleet.ch36.web;

import dev.foodfleet.ch36.jwt.JwtRejected;
import dev.foodfleet.ch36.jwt.JwtVerifier;
import dev.foodfleet.ch36.jwt.VerifiedJwt;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * RFC 6750 bearer tokens: a missing or invalid token is a 401 with a WWW-Authenticate challenge; the reason is
 * logged (and counted), never returned to the client.
 */
@Component
public class BearerTokenFilter extends OncePerRequestFilter {

    public static final String TOKEN = "accessToken";
    public static final String RAW_TOKEN = "rawAccessToken";
    private static final Logger log = LoggerFactory.getLogger(BearerTokenFilter.class);
    private final JwtVerifier verifier;

    public BearerTokenFilter(JwtVerifier verifier) {
        this.verifier = verifier;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getRequestURI().equals("/actuator/health")) {
            chain.doFilter(request, response);
            return;
        }
        String header = request.getHeader("Authorization");
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            unauthorized(response, "Bearer realm=\"foodfleet\"");
            return;
        }
        String raw = header.substring(7).trim();
        try {
            VerifiedJwt token = verifier.verify(raw);
            request.setAttribute(TOKEN, token);
            request.setAttribute(RAW_TOKEN, raw);
        } catch (JwtRejected e) {
            log.info("bearer token rejected: {}", e.reason());
            unauthorized(response, "Bearer realm=\"foodfleet\", error=\"invalid_token\"");
            return;
        }
        chain.doFilter(request, response);
    }

    private static void unauthorized(HttpServletResponse response, String challenge) throws IOException {
        response.setStatus(401);
        response.setHeader("WWW-Authenticate", challenge);
        response.setContentType("application/problem+json");
        response.getWriter().write("{\"status\":401,\"title\":\"Unauthorized\"}");
    }
}
