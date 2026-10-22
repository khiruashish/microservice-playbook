package dev.foodfleet.ch12.composition;

import java.time.Instant;

/** One line of the customer's order history, joined from three services' data. */
public record OrderHistoryEntry(String orderId, Instant placedAt, String status, long totalPaise,
                                String restaurantName, String restaurantArea, String riderName, Instant deliveredAt) {
}
