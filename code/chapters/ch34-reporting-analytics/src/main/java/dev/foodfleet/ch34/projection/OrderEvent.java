package dev.foodfleet.ch34.projection;

import java.time.Instant;

/**
 * An order's state after a change, as the order service publishes it (chapter 15's outbox). Every event carries
 * the whole state and a version that increases with each change, so the projection can drop duplicates and
 * events that arrive after a newer one.
 */
public record OrderEvent(String eventId, long orderId, int version, long restaurantId, String city, String status,
                         long amountPaise, Instant placedAt, Instant occurredAt) {
}
