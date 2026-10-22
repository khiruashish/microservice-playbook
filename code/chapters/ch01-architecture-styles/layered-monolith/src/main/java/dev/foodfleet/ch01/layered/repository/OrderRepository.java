package dev.foodfleet.ch01.layered.repository;

import dev.foodfleet.ch01.layered.domain.Order;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

/** In-memory stand-in for the single shared database every layer of the monolith uses. */
@Repository
public class OrderRepository {

    private final Map<UUID, Order> orders = new ConcurrentHashMap<>();

    public Order save(Order order) {
        orders.put(order.id(), order);
        return order;
    }

    public Optional<Order> findById(UUID id) {
        return Optional.ofNullable(orders.get(id));
    }

    public int count() {
        return orders.size();
    }
}
