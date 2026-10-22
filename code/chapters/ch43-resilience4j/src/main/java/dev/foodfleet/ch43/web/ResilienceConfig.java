package dev.foodfleet.ch43.web;

import dev.foodfleet.ch43.policy.ClientErrorException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.core.registry.EntryAddedEvent;
import io.github.resilience4j.core.registry.EntryRemovedEvent;
import io.github.resilience4j.core.registry.EntryReplacedEvent;
import io.github.resilience4j.core.registry.RegistryEventConsumer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatusCode;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.web.client.RestClient;

/** HTTP clients that tell the resilience layer which failures are the dependency's, and breaker transition logs. */
@Configuration
@EnableResilientMethods
public class ResilienceConfig {

    private static final System.Logger LOG = System.getLogger("resilience");

    /** 4xx means "your request is wrong" except 408 (timeout) and 429 (slow down), which are about load. */
    static boolean clientError(HttpStatusCode status) {
        return status.is4xxClientError() && status.value() != 408 && status.value() != 429;
    }

    private static RestClient client(RestClient.Builder builder, String baseUrl) {
        return builder.baseUrl(baseUrl)
                .defaultStatusHandler(ResilienceConfig::clientError, (request, response) -> {
                    throw new ClientErrorException(response.getStatusCode().value(), request.getMethod() + " " + request.getURI());
                })
                .build();
    }

    @Bean
    RestClient restaurantService(RestClient.Builder builder) {
        return client(builder, "http://restaurant-service");
    }

    @Bean
    RestClient paymentService(RestClient.Builder builder) {
        return client(builder, "http://payment-service");
    }

    /** Every breaker the registry creates logs its transitions: OPEN is the signal on-call needs first. */
    @Bean
    RegistryEventConsumer<CircuitBreaker> breakerTransitions() {
        return new RegistryEventConsumer<>() {
            @Override
            public void onEntryAddedEvent(EntryAddedEvent<CircuitBreaker> event) {
                logTransitions(event.getAddedEntry());
            }

            @Override
            public void onEntryRemovedEvent(EntryRemovedEvent<CircuitBreaker> event) {
                // nothing to clean up
            }

            @Override
            public void onEntryReplacedEvent(EntryReplacedEvent<CircuitBreaker> event) {
                logTransitions(event.getNewEntry());
            }
        };
    }

    private static void logTransitions(CircuitBreaker cb) {
        cb.getEventPublisher().onStateTransition(e ->
                LOG.log(System.Logger.Level.WARNING, "circuit breaker {0}: {1}", cb.getName(), e.getStateTransition()));
    }
}
