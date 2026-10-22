package dev.foodfleet.lab08;

import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

/** Two orders belonging to two customers, enough to show object-level authorization. */
@Component
public class Orders {

    public record Order(String id, String customerId, String restaurantId, long totalPaise) {
    }

    private final Map<String, Order> orders = Map.of(
            "o-1", new Order("o-1", "customer-asha", "r-1", 64700),
            "o-2", new Order("o-2", "customer-ravi", "r-1", 32900));

    public Optional<Order> find(String id) {
        return Optional.ofNullable(orders.get(id));
    }
}
