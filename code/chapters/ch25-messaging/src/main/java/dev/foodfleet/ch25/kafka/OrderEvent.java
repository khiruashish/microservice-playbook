package dev.foodfleet.ch25.kafka;

import java.time.Instant;

/**
 * An event on the {@code order-events} topic, keyed by order id so every event of one order lands
 * on the same partition, in order.
 */
public record OrderEvent(String eventId, String orderId, Type type, String restaurantId, Instant at) {

    public enum Type { PLACED, ACCEPTED, PICKED_UP, DELIVERED, CANCELLED }
}
