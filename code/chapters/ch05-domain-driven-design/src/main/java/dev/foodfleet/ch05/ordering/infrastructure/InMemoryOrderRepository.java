package dev.foodfleet.ch05.ordering.infrastructure;

import dev.foodfleet.ch05.ordering.domain.Identifiers.OrderId;
import dev.foodfleet.ch05.ordering.domain.Order;
import dev.foodfleet.ch05.ordering.domain.OrderRepository;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory adapter for the repository port. Chapter 12 replaces it with PostgreSQL (one row per
 * order plus its lines, saved in one transaction, with the version column for optimistic locking).
 */
public final class InMemoryOrderRepository implements OrderRepository {

    private final Map<OrderId, Order> orders = new ConcurrentHashMap<>();

    @Override
    public Optional<Order> findById(OrderId id) {
        return Optional.ofNullable(orders.get(id));
    }

    @Override
    public void save(Order order) {
        orders.put(order.id(), order);
    }
}
