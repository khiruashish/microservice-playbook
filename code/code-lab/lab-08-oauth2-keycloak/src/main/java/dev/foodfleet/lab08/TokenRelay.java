package dev.foodfleet.lab08;

import java.io.IOException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Passes the caller's access token on to the next service, so it can make its own decision about the same user.
 * Simple, and enough when the next service accepts the same audience. FoodFleet goes further for calls on a user's
 * behalf (ADR-036): token exchange gives the next service a token with only its own audience, fewer scopes and five
 * minutes to live, so a token leaked downstream is worth less.
 */
public class TokenRelay implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt && !request.getHeaders().containsHeader(HttpHeaders.AUTHORIZATION)) {
            request.getHeaders().setBearerAuth(jwt.getToken().getTokenValue());
        }
        return execution.execute(request, body);
    }
}
