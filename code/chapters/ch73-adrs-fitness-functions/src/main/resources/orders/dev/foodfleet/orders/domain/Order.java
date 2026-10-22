package dev.foodfleet.orders.domain;

import java.time.Instant;
import java.util.List;

public record Order(String id, String restaurant, List<OrderLine> lines, Instant placedAt, String status) {

    public Order cancel() {
        return new Order(id, restaurant, lines, placedAt, "CANCELLED");
    }
}
