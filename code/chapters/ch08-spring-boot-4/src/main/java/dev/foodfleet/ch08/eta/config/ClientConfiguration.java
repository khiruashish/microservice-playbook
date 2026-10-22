package dev.foodfleet.ch08.eta.config;

import dev.foodfleet.ch08.eta.client.KitchenClient;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.web.service.registry.ImportHttpServices;

/**
 * HTTP service clients, one group per downstream service. Each group is configured from
 * {@code spring.http.serviceclient.<group>.*}: base URL, connect and read timeouts, API version.
 * Requires {@code spring-boot-starter-restclient} on the main classpath.
 */
@Configuration(proxyBeanMethods = false)
@ImportHttpServices(group = "kitchen", types = KitchenClient.class)
@EnableResilientMethods
class ClientConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
