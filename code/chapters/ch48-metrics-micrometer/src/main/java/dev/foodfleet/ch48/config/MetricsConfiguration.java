package dev.foodfleet.ch48.config;

import dev.foodfleet.ch48.metrics.MetricsPolicy;
import dev.foodfleet.ch48.metrics.OrderMetrics;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.observation.ObservationPredicate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.observation.ServerRequestObservationContext;

/**
 * order-service's metrics in Spring Boot 4.1.1. Boot applies every {@link MeterFilter} bean to every registry before
 * meters are created, every {@link ObservationPredicate} bean to the ObservationRegistry, and binds every
 * {@code MeterBinder} bean. Common tags and the uri-tag cap are Boot properties ({@code management.metrics.tags.*},
 * {@code management.metrics.web.server.max-uri-tags}): see application.yml.
 */
@Configuration(proxyBeanMethods = false)
class MetricsConfiguration {

    @Bean
    MeterFilter neverTagWithIdentifiers() {
        return MeterFilter.ignoreTags(MetricsPolicy.NEVER_TAGS.toArray(String[]::new));
    }

    @Bean
    MeterFilter latencyHistograms() {
        return MetricsPolicy.boundedLatencyHistograms("http.server.requests", "http.client.requests", "foodfleet.");
    }

    /** Prometheus scraping /actuator/prometheus every 15 s is not traffic: keep it out of http.server.requests. */
    @Bean
    ObservationPredicate noActuatorObservations() {
        return (name, context) -> !(context instanceof ServerRequestObservationContext server
                && server.getCarrier().getRequestURI().startsWith("/actuator"));
    }

    @Bean
    OrderMetrics orderMetrics() {
        return new OrderMetrics();
    }
}
