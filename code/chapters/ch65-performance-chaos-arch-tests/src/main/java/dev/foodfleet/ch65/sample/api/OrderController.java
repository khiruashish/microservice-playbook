package dev.foodfleet.ch65.sample.api;

import dev.foodfleet.ch65.sample.application.PlaceOrder;
import dev.foodfleet.ch65.sample.domain.order.Order;

/** The HTTP edge: turns a request into a use case call and the result into a response. */
public final class OrderController {

    private final PlaceOrder placeOrder;

    public OrderController(PlaceOrder placeOrder) {
        this.placeOrder = placeOrder;
    }

    public OrderResponse post(String customer, String amount, boolean firstOrder) {
        long id = placeOrder.place(customer, amount, firstOrder);
        Order order = placeOrder.last();
        return new OrderResponse(id, order.total().toString());
    }
}
