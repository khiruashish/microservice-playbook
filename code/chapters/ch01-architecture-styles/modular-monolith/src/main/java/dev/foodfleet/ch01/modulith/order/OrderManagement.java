package dev.foodfleet.ch01.modulith.order;

import dev.foodfleet.ch01.modulith.order.internal.Order;
import dev.foodfleet.ch01.modulith.order.internal.OrderLine;
import dev.foodfleet.ch01.modulith.order.internal.OrderRepository;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Public API of the order module.
 *
 * <p>Compared with the layered monolith's {@code OrderService}:
 * <ul>
 *   <li>It does not know the kitchen or notification modules exist. It publishes {@link OrderPlaced}.</li>
 *   <li>The availability check runs <em>before</em> the transaction opens. If that check becomes
 *       a network call after extraction, no database connection is held while waiting on it.</li>
 *   <li>The order row and the event publication record are written in one local transaction, so
 *       the event is never lost even if the process crashes right after the commit.</li>
 * </ul>
 */
@Service
public class OrderManagement {

    private final OrderRepository orders;
    private final RestaurantAvailability availability;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public OrderManagement(OrderRepository orders, RestaurantAvailability availability,
                           ApplicationEventPublisher events, TransactionTemplate transactions, Clock clock) {
        this.orders = orders;
        this.availability = availability;
        this.events = events;
        this.transactions = transactions;
        this.clock = clock;
    }

    public OrderSummary placeOrder(PlaceOrder command) {
        if (!availability.canAccept(command.restaurantId())) {
            throw new RestaurantUnavailableException(command.restaurantId());
        }

        Order order = Order.place(
                command.customerId(),
                command.restaurantId(),
                command.lines().stream()
                        .map(l -> new OrderLine(l.itemId(), l.name(), l.quantity(), l.unitPricePaise()))
                        .toList(),
                clock.instant());

        return transactions.execute(status -> {
            orders.save(order);
            events.publishEvent(new OrderPlaced(order.id(), order.customerId(), order.restaurantId(),
                    order.totalPaise(), order.itemCount(), order.placedAt()));
            return toSummary(order);
        });
    }

    public Optional<OrderSummary> findOrder(UUID orderId) {
        return orders.findById(orderId).map(OrderManagement::toSummary);
    }

    private static OrderSummary toSummary(Order order) {
        return new OrderSummary(order.id(), order.customerId(), order.restaurantId(), order.status(),
                order.totalPaise(), order.itemCount(), order.placedAt());
    }
}
