package dev.foodfleet.ch37.ordersvc.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * order-service as an OAuth 2 resource server. The issuer, audience and algorithms come from application.yml
 * (Spring Boot builds the JwtDecoder with an audience validator from them); this class decides what each request
 * needs. Coarse rules here, per URL and scope; per-object rules in the service with method security.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain api(HttpSecurity http, JwtAuthenticationConverter tokens) {
        http.authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/orders/**").hasAuthority("SCOPE_orders:read")
                        .requestMatchers(HttpMethod.PATCH, "/orders/**").hasAuthority("SCOPE_orders:write")
                        .requestMatchers(HttpMethod.POST, "/orders/*/refund").hasRole("SUPPORT")
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().denyAll())                       // a new endpoint is closed until a rule opens it
                .oauth2ResourceServer(rs -> rs.jwt(jwt -> jwt.jwtAuthenticationConverter(tokens)))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable())                          // no cookies: bearer tokens aren't sent by browsers
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable());
        return http.build();
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new TokenAuthoritiesConverter());
        converter.setPrincipalClaimName("sub");
        return converter;
    }
}
