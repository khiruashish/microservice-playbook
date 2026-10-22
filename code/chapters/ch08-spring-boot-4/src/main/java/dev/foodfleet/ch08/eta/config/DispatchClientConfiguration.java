package dev.foodfleet.ch08.eta.config;

import dev.foodfleet.ch08.eta.client.DispatchClient;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.service.registry.ImportHttpServices;

/** A second group, so dispatch gets its own base URL and tighter timeouts than kitchen. */
@Configuration(proxyBeanMethods = false)
@ImportHttpServices(group = "dispatch", types = DispatchClient.class)
class DispatchClientConfiguration {
}
