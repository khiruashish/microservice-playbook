package dev.foodfleet.ch65.sample.application;

import dev.foodfleet.ch65.sample.domain.money.Money;
import dev.foodfleet.ch65.sample.domain.order.Coupon;
import dev.foodfleet.ch65.sample.domain.order.Order;

/** The use case: price an order, apply FIRST20 to a first order, store it. */
public final class PlaceOrder {

    private final OrderRepository orders;
    private Order last;

    public PlaceOrder(OrderRepository orders) {
        this.orders = orders;
    }

    public long place(String customer, String amount, boolean firstOrder) {
        Money subtotal = Money.of(amount);
        Money discount = firstOrder ? Coupon.FIRST20.discount(subtotal) : Money.ZERO;
        last = new Order(customer, subtotal, discount);
        return orders.save(last);
    }

    public Order last() {
        return last;
    }
}
