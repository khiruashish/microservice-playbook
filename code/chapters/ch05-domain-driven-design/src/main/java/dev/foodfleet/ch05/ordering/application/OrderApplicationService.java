package dev.foodfleet.ch05.ordering.application;

import dev.foodfleet.ch05.ordering.domain.CancellationReason;
import dev.foodfleet.ch05.ordering.domain.DomainEventPublisher;
import dev.foodfleet.ch05.ordering.domain.DomainException;
import dev.foodfleet.ch05.ordering.domain.Identifiers.OrderId;
import dev.foodfleet.ch05.ordering.domain.MenuCatalog;
import dev.foodfleet.ch05.ordering.domain.Order;
import dev.foodfleet.ch05.ordering.domain.OrderRepository;
import dev.foodfleet.ch05.ordering.domain.OrderableItem;
import dev.foodfleet.ch05.ordering.domain.PricingPolicy;
import dev.foodfleet.ch05.ordering.domain.RestaurantTerms;
import java.time.Clock;
import java.util.Optional;

/**
 * Application service: orchestrates one use case per method. It loads what the domain needs, calls
 * the aggregate, saves it, and publishes the recorded events. It contains <b>no business rules</b>:
 * if you find an {@code if} about orders here, it belongs in {@link Order}.
 *
 * <p>Framework-free on purpose. Spring wires it in {@code OrderingConfiguration}; tests construct it
 * with plain fakes. In production, each method runs in one transaction that touches one aggregate.
 */
public final class OrderApplicationService {

    private final OrderRepository orders;
    private final MenuCatalog catalog;
    private final DeliveryFeeQuote deliveryFees;
    private final PricingPolicy pricing;
    private final DomainEventPublisher events;
    private final Clock clock;

    public OrderApplicationService(OrderRepository orders, MenuCatalog catalog, DeliveryFeeQuote deliveryFees,
                                   PricingPolicy pricing, DomainEventPublisher events, Clock clock) {
        this.orders = orders;
        this.catalog = catalog;
        this.deliveryFees = deliveryFees;
        this.pricing = pricing;
        this.events = events;
        this.clock = clock;
    }

    public Order placeOrder(PlaceOrderCommand command) {
        RestaurantTerms terms = catalog.termsFor(command.restaurantId())
                .orElseThrow(() -> new DomainException("Unknown restaurant " + command.restaurantId().value()));

        Order order = Order.startDraft(OrderId.newId(), command.customerId(), command.restaurantId(), command.address());
        for (PlaceOrderCommand.Item line : command.items()) {
            OrderableItem item = catalog.findItem(command.restaurantId(), line.menuItemId())
                    .orElseThrow(() -> new DomainException("Unknown dish " + line.menuItemId().value()));
            order.addItem(item, line.quantity());
        }
        order.place(pricing, terms, deliveryFees.feeFor(command.address()), clock.instant());

        orders.save(order);
        events.publish(order.pullEvents());
        return order;
    }

    public Order cancel(OrderId orderId, CancellationReason reason) {
        Order order = orders.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
        order.cancel(reason, clock.instant());
        orders.save(order);
        events.publish(order.pullEvents());
        return order;
    }

    public Optional<Order> find(OrderId orderId) {
        return orders.findById(orderId);
    }
}
