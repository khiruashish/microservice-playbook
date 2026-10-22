package dev.foodfleet.ch38.web.payments;

import dev.foodfleet.ch38.authz.PeerPolicy;
import dev.foodfleet.ch38.spiffe.SpiffeId;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * payment-service: two independent checks on every request. The workload identity from mutual TLS must be allowed
 * on the route ({@link PeerPolicyFilter}), and the access token must carry the scope (Spring's resource server, with
 * issuer, audience and algorithms from payment-service.yml). A stolen token is useless from another workload; a
 * compromised workload can do only what its tokens allow.
 */
@Configuration(proxyBeanMethods = false)
public class PaymentSecurityConfig {

    static final String TRUST_DOMAIN = "foodfleet.internal";
    static final SpiffeId ORDER_SERVICE = SpiffeId.ofWorkload(TRUST_DOMAIN, "orders", "order-service");
    static final SpiffeId SETTLEMENT_JOB = SpiffeId.ofWorkload(TRUST_DOMAIN, "payments", "settlement-job");

    @Bean
    PeerPolicy peerPolicy() {
        return PeerPolicy.builder()
                .allow("capture", "POST", "/payments/*/capture", ORDER_SERVICE)
                .allow("refund", "POST", "/payments/*/refund", ORDER_SERVICE)
                .allow("settle", "POST", "/internal/settlements/run", SETTLEMENT_JOB)
                .build();
    }

    @Bean
    SecurityFilterChain payments(HttpSecurity http, PeerPolicy peerPolicy) {
        http.addFilterBefore(new PeerPolicyFilter(peerPolicy, Clock.systemUTC()), BearerTokenAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/payments/*/capture").hasAuthority("SCOPE_payments:capture")
                        .requestMatchers(HttpMethod.POST, "/payments/*/refund").hasAuthority("SCOPE_payments:refund")
                        .requestMatchers(HttpMethod.POST, "/internal/settlements/run").hasAuthority("SCOPE_settlements:run")
                        .anyRequest().denyAll())
                .oauth2ResourceServer(rs -> rs.jwt(jwt -> { }))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable());
        return http.build();
    }
}
