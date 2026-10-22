package dev.foodfleet.ch12.composition;

import dev.foodfleet.ch12.composition.Ports.DeliveryInfo;
import dev.foodfleet.ch12.composition.Ports.OrderRow;
import dev.foodfleet.ch12.composition.Ports.RestaurantInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * What API composition usually looks like on day one: a loop with a remote call per row and per
 * service, in sequence, where any failure fails the page. Kept for comparison: 1 + 2N calls.
 */
public final class NaiveOrderHistoryComposer {

    private final Ports.Ordering ordering;
    private final Ports.Catalog catalog;
    private final Ports.Delivery delivery;

    public NaiveOrderHistoryComposer(Ports.Ordering ordering, Ports.Catalog catalog, Ports.Delivery delivery) {
        this.ordering = ordering;
        this.catalog = catalog;
        this.delivery = delivery;
    }

    public OrderHistory history(String customerId, int limit) {
        List<OrderHistoryEntry> entries = new ArrayList<>();
        for (OrderRow o : ordering.recentOrders(customerId, limit)) {
            RestaurantInfo r = catalog.restaurants(Set.of(o.restaurantId())).get(o.restaurantId());
            DeliveryInfo d = delivery.deliveries(Set.of(o.orderId())).get(o.orderId());
            entries.add(new OrderHistoryEntry(o.orderId(), o.placedAt(), o.status(), o.totalPaise(),
                    r == null ? null : r.name(), r == null ? null : r.area(),
                    d == null ? null : d.riderName(), d == null ? null : d.deliveredAt()));
        }
        return new OrderHistory(customerId, entries, List.of());
    }
}
