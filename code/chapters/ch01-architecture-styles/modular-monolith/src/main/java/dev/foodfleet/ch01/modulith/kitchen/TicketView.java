package dev.foodfleet.ch01.modulith.kitchen;

import java.time.Instant;
import java.util.UUID;

public record TicketView(UUID ticketId, UUID orderId, String restaurantId, String status, Instant createdAt) {
}
