package dev.foodfleet.ch01.layered.web;

import dev.foodfleet.ch01.layered.domain.Order;
import java.time.Instant;
import java.util.UUID;

public record OrderResponse(UUID orderId, String status, long totalPaise, Instant placedAt) {

    static OrderResponse from(Order order) {
        return new OrderResponse(order.id(), order.status().name(), order.totalPaise(), order.placedAt());
    }
}
