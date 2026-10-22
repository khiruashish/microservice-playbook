package dev.foodfleet.ch01.modulith.order;

import java.util.List;
import java.util.Objects;

/** Command accepted by the order module's public API. */
public record PlaceOrder(String customerId, String restaurantId, List<Line> lines) {

    public PlaceOrder {
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(restaurantId, "restaurantId");
        lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
    }

    public record Line(String itemId, String name, int quantity, long unitPricePaise) {
    }
}
