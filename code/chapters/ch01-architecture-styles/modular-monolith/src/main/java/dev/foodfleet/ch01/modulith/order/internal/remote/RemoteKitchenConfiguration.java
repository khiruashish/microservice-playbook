package dev.foodfleet.ch01.modulith.order.internal.remote;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.service.registry.ImportHttpServices;

/**
 * Registers {@link KitchenAvailabilityClient} in the "kitchen" HTTP service group.
 * Spring Boot 4 binds {@code spring.http.serviceclient.kitchen.base-url}, {@code connect-timeout}
 * and {@code read-timeout} to that group. This replaces the hand-built RestClient plus
 * HttpServiceProxyFactory wiring that Boot 3 required.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "foodfleet.kitchen.mode", havingValue = "remote")
@ImportHttpServices(group = "kitchen", types = KitchenAvailabilityClient.class)
class RemoteKitchenConfiguration {
}
