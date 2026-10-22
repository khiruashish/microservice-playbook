package dev.foodfleet.ch27.api;

import java.time.Instant;

/**
 * The representation of {@code GET /orders/{id}}, version 1, as published in
 * {@code static/contracts/order-api.yaml}. Additive changes only; anything else is a new version.
 */
public record OrderView(String id, Status status, Integer etaMinutes, String riderName, long totalPaise,
                        Instant placedAt, Restaurant restaurant) {

    public enum Status { PLACED, ACCEPTED, PREPARING, READY_FOR_PICKUP, PICKED_UP, DELIVERED, CANCELLED }

    public record Restaurant(String id, String name) {
    }
}
