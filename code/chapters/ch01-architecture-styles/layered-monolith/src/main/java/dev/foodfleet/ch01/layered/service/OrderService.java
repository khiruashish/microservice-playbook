package dev.foodfleet.ch01.layered.service;

import dev.foodfleet.ch01.layered.domain.Order;
import dev.foodfleet.ch01.layered.domain.OrderLine;
import dev.foodfleet.ch01.layered.domain.OrderStatus;
import dev.foodfleet.ch01.layered.repository.OrderRepository;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * The heart of the coupling problem. Placing an order calls the kitchen and notification
 * code directly and synchronously:
 * <ul>
 *   <li>If the kitchen code throws, the customer's order fails (temporal coupling).</li>
 *   <li>A change to KitchenService's signature forces a change here (code coupling).</li>
 *   <li>All three capabilities ship, scale and fail together (deployment coupling).</li>
 * </ul>
 * None of this is wrong for a small team. It becomes painful as teams and traffic grow.
 */
@Service
public class OrderService {

    private final OrderRepository orders;
    private final KitchenService kitchen;
    private final NotificationService notifications;
    private final Clock clock;

    public OrderService(OrderRepository orders, KitchenService kitchen,
                        NotificationService notifications, Clock clock) {
        this.orders = orders;
        this.kitchen = kitchen;
        this.notifications = notifications;
        this.clock = clock;
    }

    public Order placeOrder(String customerId, String restaurantId, List<OrderLine> lines) {
        Order order = Order.place(customerId, restaurantId, lines, clock.instant());
        kitchen.createTicket(order);                      // direct call: kitchen failure = order failure
        Order confirmed = orders.save(order.withStatus(OrderStatus.CONFIRMED));
        notifications.orderConfirmed(confirmed);          // direct call: slow SMS gateway = slow checkout
        return confirmed;
    }

    public Optional<Order> findOrder(UUID orderId) {
        return orders.findById(orderId);
    }
}
