package dev.foodfleet.ch36.web;

import dev.foodfleet.ch36.discovery.JwksClient;
import dev.foodfleet.ch36.exchange.TokenExchangeClient;
import dev.foodfleet.ch36.jwt.JwtVerifier;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * order-service as an OAuth resource server, built from this chapter's protocol code so every check is visible.
 * Chapter 37 replaces it with Spring Security's resource server, configured by the same values.
 */
@Configuration(proxyBeanMethods = false)
public class OAuthBeans {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    JwksClient jwksClient(@Value("${app.oauth.issuer}") URI issuer, Clock clock) {
        return JwksClient.http(issuer, clock);
    }

    @Bean
    JwtVerifier jwtVerifier(@Value("${app.oauth.issuer}") String issuer, @Value("${app.oauth.audience}") String audience,
                            JwksClient keys, Clock clock) {
        return new JwtVerifier(issuer, audience, Set.of("RS256", "ES256"), keys::key, clock, Duration.ofSeconds(30));
    }

    @Bean
    TokenExchangeClient tokenExchangeClient(@Value("${app.oauth.issuer}") String issuer,
                                            @Value("${app.oauth.client-id}") String clientId,
                                            @Value("${app.oauth.client-secret}") String secret, Clock clock) {
        return new TokenExchangeClient(URI.create(issuer + "/token"), clientId, secret, clock);
    }
}
