package dev.foodfleet.ch18.app;

import org.springframework.context.annotation.Configuration;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.web.service.registry.ImportHttpServices;

@Configuration(proxyBeanMethods = false)
@ImportHttpServices(group = "maps", types = MapsClient.class)
@EnableResilientMethods          // activates @Retryable and @ConcurrencyLimit
class ClientConfiguration {
}
