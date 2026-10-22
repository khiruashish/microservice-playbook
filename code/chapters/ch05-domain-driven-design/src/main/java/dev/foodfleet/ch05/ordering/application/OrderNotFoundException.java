package dev.foodfleet.ch05.ordering.application;

import dev.foodfleet.ch05.ordering.domain.Identifiers.OrderId;

public class OrderNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public OrderNotFoundException(OrderId orderId) {
        super("No order " + orderId.value());
    }
}
