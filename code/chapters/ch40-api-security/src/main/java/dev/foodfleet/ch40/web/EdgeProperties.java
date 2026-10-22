package dev.foodfleet.ch40.web;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** foodfleet.edge.* in order-service.yml. */
@ConfigurationProperties("foodfleet.edge")
public record EdgeProperties(Cors cors, Limit perClient, Limit perTenant, Limit anonymous, int instances) {

    public record Cors(Set<String> allowedOrigins, Set<String> allowedMethods, Set<String> allowedHeaders,
            List<String> exposedHeaders, Duration maxAge) {
    }

    public record Limit(int requests, Duration window, int burst) {
    }
}
