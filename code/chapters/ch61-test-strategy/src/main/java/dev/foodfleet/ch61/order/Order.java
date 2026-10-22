package dev.foodfleet.ch61.order;

import java.math.BigDecimal;
import java.util.List;

/** A placed order as stored and returned. */
public record Order(long id, String idempotencyKey, String customerId, int restaurantId, List<Line> lines, BigDecimal subtotal,
                    BigDecimal discount, BigDecimal tax, BigDecimal total, String status) {

    public record Line(String itemId, int quantity, BigDecimal unitPrice) {
    }

    /** What the customer asked for. */
    public record Request(String idempotencyKey, String customerId, int restaurantId, List<Item> items, String coupon) {
    }

    public record Item(String itemId, int quantity) {
    }
}
