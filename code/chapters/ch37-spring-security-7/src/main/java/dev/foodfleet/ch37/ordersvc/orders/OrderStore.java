package dev.foodfleet.ch37.ordersvc.orders;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

/** In memory, to keep the chapter about security. */
@Repository
public class OrderStore {

    private final ConcurrentHashMap<Long, Order> orders = new ConcurrentHashMap<>();

    public void save(Order o) {
        orders.put(o.id(), o);
    }

    public Optional<Order> find(long id) {
        return Optional.ofNullable(orders.get(id));
    }
}
