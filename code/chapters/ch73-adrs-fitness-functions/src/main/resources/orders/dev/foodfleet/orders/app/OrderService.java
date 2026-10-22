package dev.foodfleet.orders.app;

import dev.foodfleet.orders.api.OrderApi;
import dev.foodfleet.orders.domain.Order;
import dev.foodfleet.orders.domain.Pricing;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class OrderService implements OrderApi {

    private final Map<String, Order> orders = new ConcurrentHashMap<>();
    private final Pricing pricing;

    public OrderService(Pricing pricing) {
        this.pricing = pricing;
    }

    public void add(Order order) {
        orders.put(order.id(), order);
    }

    @Override
    public Optional<OrderView> get(String id) {
        Order o = orders.get(id);
        return o == null ? Optional.empty()
                : Optional.of(new OrderView(o.id(), o.restaurant(), o.lines().size(), pricing.total(o), o.placedAt().toString(), o.status()));
    }

    @Override
    public boolean cancel(String id) {
        return orders.computeIfPresent(id, (k, o) -> o.cancel()) != null;
    }
}
