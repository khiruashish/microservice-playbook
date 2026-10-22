package dev.foodfleet.ch02.menu.config;

import dev.foodfleet.ch02.menu.pricing.PricingClient;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.web.service.registry.ImportHttpServices;

/**
 * Cross-cutting wiring, kept out of business code.
 * <ul>
 *   <li>{@code @EnableResilientMethods} activates Spring Framework 7's {@code @Retryable} and
 *       {@code @ConcurrencyLimit} proxies. Spring Boot does not switch them on for you.</li>
 *   <li>{@code @ImportHttpServices} registers the pricing-service client. Base URL and timeouts come
 *       from {@code spring.http.serviceclient.pricing.*} (externalized config, 12-factor III).</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@EnableResilientMethods
@ImportHttpServices(group = "pricing", types = PricingClient.class)
public class PlatformConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
