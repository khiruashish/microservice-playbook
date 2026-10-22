package dev.foodfleet.ch61.order;

import java.util.ArrayList;
import java.util.List;

/** Placing an order: validate, price from menu-service, apply the money rules, store idempotently. */
public final class OrderService {

    private final MenuClient menu;
    private final OrderRepository orders;

    public OrderService(MenuClient menu, OrderRepository orders) {
        this.menu = menu;
        this.orders = orders;
    }

    public OrderRepository.Stored place(Order.Request r) {
        if (r.idempotencyKey() == null || r.idempotencyKey().isBlank()) {
            throw new IllegalArgumentException("idempotency key required");
        }
        if (r.items() == null || r.items().isEmpty()) {
            throw new IllegalArgumentException("no items");
        }
        List<Order.Line> lines = new ArrayList<>();
        for (Order.Item i : r.items()) {
            if (i.quantity() < 1 || i.quantity() > 20) {
                throw new IllegalArgumentException("quantity must be 1 to 20: " + i.itemId());
            }
            MenuClient.Price p = menu.price(r.restaurantId(), i.itemId());
            if (!p.available()) {
                throw new IllegalArgumentException("not available: " + i.itemId());
            }
            lines.add(new Order.Line(i.itemId(), i.quantity(), p.price()));
        }
        Pricing.Totals t = Pricing.price(lines, r.coupon());
        return orders.insertIfAbsent(new Order(0, r.idempotencyKey(), r.customerId(), r.restaurantId(), lines, t.subtotal(), t.discount(), t.tax(),
                t.total(), "PLACED"));
    }
}
