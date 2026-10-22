package dev.foodfleet.ch11.kitchen;

import org.springframework.context.annotation.Configuration;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.web.service.registry.ImportHttpServices;

/** Group name = service id in the registry. */
@Configuration(proxyBeanMethods = false)
@ImportHttpServices(group = "kitchen-service", types = KitchenClient.class)
@EnableResilientMethods
class KitchenClientConfiguration {
}
