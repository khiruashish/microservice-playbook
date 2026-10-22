package dev.foodfleet.ch22.client;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.service.registry.ImportHttpServices;
import tools.jackson.databind.json.JsonMapper;

/**
 * One group per remote service. Spring Boot binds {@code spring.http.serviceclient.order-history.*}
 * (base URL, timeouts) to the group; this configurer adds what properties can't express.
 */
@Configuration(proxyBeanMethods = false)
@ImportHttpServices(group = "order-history", types = OrderHistoryApi.class)
class ClientConfiguration {

    @Bean
    RestClientHttpServiceGroupConfigurer orderHistoryClientConfigurer(JsonMapper json) {
        return groups -> groups.filterByName("order-history").forEachClient((group, builder) -> builder
                .defaultStatusHandler(HttpStatusCode::isError, new ProblemDetailErrorHandler(json))
                .defaultHeader("User-Agent", "tracking-service"));
    }
}
