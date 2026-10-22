package dev.foodfleet.ch15.app.order;

/** The event published when an order is placed (the outbox payload, as JSON). */
public record OrderPlaced(String orderId, String customerId, long totalPaise, long points) {
}
