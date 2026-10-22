package dev.foodfleet.ch05.ordering.domain;

import dev.foodfleet.ch05.ordering.domain.Identifiers.OrderId;
import java.util.Optional;

/**
 * Repository: a collection-like port for <em>aggregate roots only</em>. There is no
 * OrderLineRepository, because lines are loaded and saved with their order. The domain defines
 * the interface; infrastructure implements it.
 */
public interface OrderRepository {

    Optional<Order> findById(OrderId id);

    void save(Order order);
}
