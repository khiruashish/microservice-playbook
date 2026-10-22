package dev.foodfleet.ch19.app.catalog;

import java.time.Instant;

/**
 * Integration event, schema v1: the menu item as it now is. It carries everything the known
 * consumers need (state transfer), plus {@code version} so consumers can ignore duplicates and
 * out-of-order deliveries, and {@code eventId} for tracing and deduplication.
 *
 * <p>Evolution rules (chapter 27): add optional fields only; never rename or repurpose one; a
 * breaking change is a new event type on a new topic, published alongside the old one.
 */
public record MenuItemUpdated(String eventId, String itemId, String restaurantId, String name, long pricePaise,
                              boolean available, long version, Instant occurredAt) {
}
