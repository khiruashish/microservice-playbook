package dev.foodfleet.ch01.modulith.order;

import java.time.Instant;
import java.util.UUID;

/** Read model the order module hands to other modules and to its own web layer. */
public record OrderSummary(
        UUID orderId,
        String customerId,
        String restaurantId,
        OrderStatus status,
        long totalPaise,
        int itemCount,
        Instant placedAt) {
}
