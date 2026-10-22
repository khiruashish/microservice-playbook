package dev.foodfleet.ch01.modulith.order.internal;

import java.util.Objects;

public record OrderLine(String itemId, String name, int quantity, long unitPricePaise) {

    public OrderLine {
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(name, "name");
        if (quantity < 1) {
            throw new IllegalArgumentException("quantity must be >= 1");
        }
        if (unitPricePaise < 0) {
            throw new IllegalArgumentException("unitPricePaise must be >= 0");
        }
    }

    public long linePaise() {
        return Math.multiplyExact(unitPricePaise, quantity);
    }
}
