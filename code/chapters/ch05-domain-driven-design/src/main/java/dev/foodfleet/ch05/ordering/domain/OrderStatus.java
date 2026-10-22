package dev.foodfleet.ch05.ordering.domain;

import java.util.EnumSet;
import java.util.Set;

/** The order lifecycle as the Ordering context understands it. Allowed transitions are explicit. */
public enum OrderStatus {
    DRAFT,
    PLACED,
    ACCEPTED,
    PREPARING,
    OUT_FOR_DELIVERY,
    DELIVERED,
    CANCELLED;

    public Set<OrderStatus> next() {
        return switch (this) {
            case DRAFT -> EnumSet.of(PLACED, CANCELLED);
            case PLACED -> EnumSet.of(ACCEPTED, CANCELLED);
            case ACCEPTED -> EnumSet.of(PREPARING, CANCELLED);
            case PREPARING -> EnumSet.of(OUT_FOR_DELIVERY, CANCELLED);
            case OUT_FOR_DELIVERY -> EnumSet.of(DELIVERED);
            case DELIVERED, CANCELLED -> EnumSet.noneOf(OrderStatus.class);
        };
    }

    public boolean canMoveTo(OrderStatus target) {
        return next().contains(target);
    }
}
