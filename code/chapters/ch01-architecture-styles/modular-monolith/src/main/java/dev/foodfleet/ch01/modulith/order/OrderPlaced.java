package dev.foodfleet.ch01.modulith.order;

import java.time.Instant;
import java.util.UUID;
import org.springframework.modulith.events.Externalized;

/**
 * Domain event published when an order is placed. Other modules depend on this type,
 * never on the order module's internals.
 *
 * <p>{@code @Externalized} is a no-op until a broker module (for example
 * {@code spring-modulith-events-kafka}) is on the classpath. Adding that dependency later
 * publishes this exact event to the {@code foodfleet.order.placed.v1} topic, keyed by
 * restaurant, which is the first step in extracting a consumer module into its own service.
 */
@Externalized("foodfleet.order.placed.v1::#{restaurantId()}")
public record OrderPlaced(
        UUID orderId,
        String customerId,
        String restaurantId,
        long totalPaise,
        int itemCount,
        Instant placedAt) {
}
