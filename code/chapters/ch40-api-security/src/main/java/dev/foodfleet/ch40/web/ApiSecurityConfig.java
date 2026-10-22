package dev.foodfleet.ch40.web;

import dev.foodfleet.ch40.csrf.FetchMetadataPolicy;
import dev.foodfleet.ch40.ratelimit.GcraLimiter;
import dev.foodfleet.ch40.ratelimit.LayeredLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;

/**
 * Two kinds of client, two filter chains.
 * <ul>
 * <li>/bff: the web app. The browser holds only a session cookie (Secure, HttpOnly, SameSite=Lax); tokens stay
 * on the server. Cookies are sent automatically, so cross-site requests must be stopped: Fetch Metadata first,
 * then Spring Security's CSRF token (the SPA variant: an XSRF-TOKEN cookie the app copies into a header).</li>
 * <li>/api: apps, partners, the restaurant dashboard. Bearer tokens in the Authorization header, which no browser
 * attaches by itself, so there is no CSRF to defend against and the CSRF filter is off; no session; CORS for the
 * listed origins only; rate limits keyed by the token's tenant and client.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
class ApiSecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain bff(HttpSecurity http, FetchMetadataPolicy fetchMetadata) throws Exception {
        http.securityMatcher("/bff/**")
                .authorizeHttpRequests(a -> a.anyRequest().authenticated())
                .oauth2Login(Customizer.withDefaults())
                .csrf(csrf -> csrf.spa())
                .addFilterBefore(new FetchMetadataFilter(fetchMetadata), CsrfFilter.class);
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain api(HttpSecurity http, LayeredLimiter sharedLimits, LayeredLimiter fallbackLimits,
            GcraLimiter anonymousLimits) throws Exception {
        http.securityMatcher("/api/**")
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.GET, "/api/restaurants", "/api/restaurants/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .addFilterAfter(new RateLimitFilter(sharedLimits, fallbackLimits, anonymousLimits),
                        BearerTokenAuthenticationFilter.class);
        return http.build();
    }
}
