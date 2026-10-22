package dev.foodfleet.ch10.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Where the backends live and how hard the edge limits clients. In Kubernetes the backend URLs
 * are service DNS names (chapter 11); locally they're ports.
 */
@ConfigurationProperties("foodfleet.edge")
public record EdgeProperties(Backends backends, @DefaultValue RateLimit placeOrderRateLimit) {

    public record Backends(String menu, String ordering, String eta, String payment, String mobileBff) {
    }

    /** Token bucket per client: {@code capacity} requests in a burst, refilled at {@code refillPerMinute}. */
    public record RateLimit(@DefaultValue("5") int capacity, @DefaultValue("10") int refillPerMinute,
                            @DefaultValue("100000") int maxClients) {
    }
}
