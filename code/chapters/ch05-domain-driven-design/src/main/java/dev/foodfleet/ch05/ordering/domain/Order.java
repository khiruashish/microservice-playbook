package dev.foodfleet.ch05.ordering.domain;

import dev.foodfleet.ch05.ordering.domain.Identifiers.CustomerId;
import dev.foodfleet.ch05.ordering.domain.Identifiers.MenuItemId;
import dev.foodfleet.ch05.ordering.domain.Identifiers.OrderId;
import dev.foodfleet.ch05.ordering.domain.Identifiers.RestaurantId;
import dev.foodfleet.ch05.ordering.domain.OrderEvent.DeliveryAddressChanged;
import dev.foodfleet.ch05.ordering.domain.OrderEvent.OrderAccepted;
import dev.foodfleet.ch05.ordering.domain.OrderEvent.OrderCancelled;
import dev.foodfleet.ch05.ordering.domain.OrderEvent.OrderDelivered;
import dev.foodfleet.ch05.ordering.domain.OrderEvent.OrderDispatched;
import dev.foodfleet.ch05.ordering.domain.OrderEvent.OrderPlaced;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * <b>Aggregate root</b> of the Ordering context. Everything inside it (its lines, its address,
 * its price) changes only through this class's methods, and every method protects the
 * aggregate's invariants:
 * <ol>
 *   <li>An order contains dishes from exactly one restaurant.</li>
 *   <li>Only available dishes can be added; at most {@value #MAX_DISTINCT_DISHES} distinct dishes and
 *       {@value #MAX_TOTAL_ITEMS} items in total.</li>
 *   <li>Lines and address can change only while the order is a draft (the address also while PLACED).</li>
 *   <li>An order can be placed only if the restaurant is accepting orders and the item total meets its minimum.</li>
 *   <li>Status changes follow {@link OrderStatus#next()}; out-for-delivery orders can't be cancelled.</li>
 *   <li>The price is fixed at placement and never recalculated afterwards.</li>
 * </ol>
 * Other aggregates (Customer, Restaurant, Rider) are referenced <b>by id only</b>, never by object.
 * One transaction modifies one aggregate; changes elsewhere happen through the events recorded here.
 */
public final class Order {

    public static final int MAX_DISTINCT_DISHES = 15;
    public static final int MAX_TOTAL_ITEMS = 50;

    private final OrderId id;
    private final CustomerId customerId;
    private final RestaurantId restaurantId;
    private final Map<MenuItemId, OrderLine> lines = new LinkedHashMap<>();
    private final List<OrderEvent> pendingEvents = new ArrayList<>();
    private DeliveryAddress deliveryAddress;
    private OrderStatus status = OrderStatus.DRAFT;
    private PriceBreakdown price;
    private long version;

    private Order(OrderId id, CustomerId customerId, RestaurantId restaurantId, DeliveryAddress deliveryAddress) {
        this.id = Objects.requireNonNull(id);
        this.customerId = Objects.requireNonNull(customerId);
        this.restaurantId = Objects.requireNonNull(restaurantId);
        this.deliveryAddress = Objects.requireNonNull(deliveryAddress);
    }

    /** Factory: the only way to create an order, so a new order is always valid. */
    public static Order startDraft(OrderId id, CustomerId customerId, RestaurantId restaurantId,
                                  DeliveryAddress deliveryAddress) {
        return new Order(id, customerId, restaurantId, deliveryAddress);
    }

    public void addItem(OrderableItem item, Quantity quantity) {
        requireStatus(OrderStatus.DRAFT, "add dishes");
        if (!item.restaurantId().equals(restaurantId)) {
            throw new DomainException("An order can contain dishes from one restaurant only");
        }
        if (!item.available()) {
            throw new DomainException(item.name() + " is not available right now");
        }
        OrderLine existing = lines.get(item.id());
        if (existing == null && lines.size() == MAX_DISTINCT_DISHES) {
            throw new DomainException("An order can have at most " + MAX_DISTINCT_DISHES + " different dishes");
        }
        if (itemCount() + quantity.value() > MAX_TOTAL_ITEMS) {
            throw new DomainException("An order can have at most " + MAX_TOTAL_ITEMS + " items");
        }
        if (existing == null) {
            lines.put(item.id(), new OrderLine(item.id(), item.name(), item.price(), quantity));
        } else {
            existing.increaseBy(quantity); // keeps the original price snapshot
        }
        version++;
    }

    public void removeItem(MenuItemId menuItemId) {
        requireStatus(OrderStatus.DRAFT, "remove dishes");
        if (lines.remove(menuItemId) != null) {
            version++;
        }
    }

    public void changeDeliveryAddress(DeliveryAddress newAddress, Instant now) {
        if (status != OrderStatus.DRAFT && status != OrderStatus.PLACED) {
            throw new DomainException("The delivery address can't change once the restaurant has accepted the order");
        }
        deliveryAddress = Objects.requireNonNull(newAddress);
        if (status == OrderStatus.PLACED) {
            pendingEvents.add(new DeliveryAddressChanged(id, newAddress, now));
        }
        version++;
    }

    public void place(PricingPolicy pricing, RestaurantTerms terms, Money deliveryFee, Instant now) {
        requireStatus(OrderStatus.DRAFT, "place the order");
        if (!terms.restaurantId().equals(restaurantId)) {
            throw new IllegalArgumentException("Terms belong to a different restaurant");
        }
        if (!terms.acceptingOrders()) {
            throw new DomainException("The restaurant is not accepting orders right now");
        }
        if (lines.isEmpty()) {
            throw new DomainException("Add at least one dish before placing the order");
        }
        Money itemTotal = itemTotal();
        if (itemTotal.isLessThan(terms.minimumOrder())) {
            throw new DomainException("Minimum order is " + terms.minimumOrder() + "; your items total " + itemTotal);
        }
        price = pricing.price(lines.values(), terms, deliveryFee);
        transitionTo(OrderStatus.PLACED);
        pendingEvents.add(new OrderPlaced(id, customerId, restaurantId, price.total(), itemCount(), now));
    }

    public void accept(Instant now) {
        transitionTo(OrderStatus.ACCEPTED);
        pendingEvents.add(new OrderAccepted(id, now));
    }

    public void startPreparing() {
        transitionTo(OrderStatus.PREPARING);
    }

    public void dispatch(String riderId, Instant now) {
        transitionTo(OrderStatus.OUT_FOR_DELIVERY);
        pendingEvents.add(new OrderDispatched(id, riderId, now));
    }

    public void deliver(Instant now) {
        transitionTo(OrderStatus.DELIVERED);
        pendingEvents.add(new OrderDelivered(id, now));
    }

    public void cancel(CancellationReason reason, Instant now) {
        if (!status.canMoveTo(OrderStatus.CANCELLED)) {
            throw new DomainException("An order that is " + status + " can no longer be cancelled");
        }
        Money refund = status == OrderStatus.DRAFT ? Money.ZERO : price.total();
        transitionTo(OrderStatus.CANCELLED);
        pendingEvents.add(new OrderCancelled(id, reason, refund, now));
    }

    /** Hands recorded events to the application layer exactly once. */
    public List<OrderEvent> pullEvents() {
        List<OrderEvent> events = List.copyOf(pendingEvents);
        pendingEvents.clear();
        return events;
    }

    public Money itemTotal() {
        return lines.values().stream().map(OrderLine::lineTotal).reduce(Money.ZERO, Money::plus);
    }

    public int itemCount() {
        return lines.values().stream().mapToInt(line -> line.quantity().value()).sum();
    }

    public OrderId id() {
        return id;
    }

    public CustomerId customerId() {
        return customerId;
    }

    public RestaurantId restaurantId() {
        return restaurantId;
    }

    public DeliveryAddress deliveryAddress() {
        return deliveryAddress;
    }

    public OrderStatus status() {
        return status;
    }

    public List<OrderLine> lines() {
        return List.copyOf(lines.values());
    }

    /** Empty until the order is placed. */
    public Optional<PriceBreakdown> price() {
        return Optional.ofNullable(price);
    }

    /** Incremented on every change; a repository uses it for optimistic locking. */
    public long version() {
        return version;
    }

    private void transitionTo(OrderStatus target) {
        if (!status.canMoveTo(target)) {
            throw new DomainException("An order can't go from " + status + " to " + target);
        }
        status = target;
        version++;
    }

    private void requireStatus(OrderStatus required, String action) {
        if (status != required) {
            throw new DomainException("Can't " + action + ": the order is " + status);
        }
    }
}
