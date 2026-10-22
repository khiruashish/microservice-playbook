package dev.foodfleet.ch01.layered.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** A FoodFleet order. Money is held in paise (1 INR = 100 paise) to avoid floating-point errors. */
public record Order(
        UUID id,
        String customerId,
        String restaurantId,
        List<OrderLine> lines,
        OrderStatus status,
        Instant placedAt) {

    public Order {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(restaurantId, "restaurantId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(placedAt, "placedAt");
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("An order needs at least one line");
        }
        lines = List.copyOf(lines);
    }

    public static Order place(String customerId, String restaurantId, List<OrderLine> lines, Instant now) {
        return new Order(UUID.randomUUID(), customerId, restaurantId, lines, OrderStatus.PLACED, now);
    }

    public long totalPaise() {
        return lines.stream().mapToLong(OrderLine::linePaise).sum();
    }

    public Order withStatus(OrderStatus newStatus) {
        return new Order(id, customerId, restaurantId, lines, newStatus, placedAt);
    }
}
