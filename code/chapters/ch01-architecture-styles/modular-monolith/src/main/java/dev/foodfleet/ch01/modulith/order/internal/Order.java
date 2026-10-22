package dev.foodfleet.ch01.modulith.order.internal;

import dev.foodfleet.ch01.modulith.order.OrderStatus;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Order aggregate. Internal to the order module: other modules only ever see OrderSummary/OrderPlaced. */
public record Order(
        UUID id,
        String customerId,
        String restaurantId,
        List<OrderLine> lines,
        OrderStatus status,
        Instant placedAt) {

    public static final int MAX_LINES = 50;

    public Order {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(restaurantId, "restaurantId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(placedAt, "placedAt");
        if (lines == null || lines.isEmpty() || lines.size() > MAX_LINES) {
            throw new IllegalArgumentException("An order needs between 1 and " + MAX_LINES + " lines");
        }
        lines = List.copyOf(lines);
    }

    public static Order place(String customerId, String restaurantId, List<OrderLine> lines, Instant now) {
        return new Order(UUID.randomUUID(), customerId, restaurantId, lines, OrderStatus.PLACED, now);
    }

    public long totalPaise() {
        return lines.stream().mapToLong(OrderLine::linePaise).sum();
    }

    public int itemCount() {
        return lines.stream().mapToInt(OrderLine::quantity).sum();
    }
}
