package dev.foodfleet.ch61.order;

import java.util.List;
import java.util.Optional;

public interface OrderRepository {

    /** The stored order, and whether this call created it (false: an order with the same idempotency key existed). */
    record Stored(Order order, boolean created) {
    }

    /** Stores the order unless one with the same idempotency key exists. */
    Stored insertIfAbsent(Order order);

    Optional<Order> findById(long id);

    /** The customer's most recent orders, newest first. */
    List<Order> recent(String customerId, int limit);

    /** How many orders a restaurant has received. */
    int countForRestaurant(int restaurantId);
}
