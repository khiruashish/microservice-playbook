package dev.foodfleet.ch12.composition;

import dev.foodfleet.ch12.composition.Ports.DeliveryInfo;
import dev.foodfleet.ch12.composition.Ports.OrderRow;
import dev.foodfleet.ch12.composition.Ports.RestaurantInfo;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * API composition, done carefully:
 * <ol>
 *   <li><b>Driver query first</b>: the owner of the list (ordering) decides which rows exist,
 *       their order and the page size.</li>
 *   <li><b>Batch lookups</b>: one call per enriching service with all ids, never one per row.</li>
 *   <li><b>In parallel</b> on virtual threads, within one time budget.</li>
 *   <li><b>Partial results</b>: enrichment that fails is left empty and reported, the list still shows.</li>
 *   <li><b>In-memory join</b> by id: the "SQL JOIN" moves into the composer.</li>
 * </ol>
 * Total calls: 3, whatever the page size. The naive version makes 1 + 2N.
 */
public final class OrderHistoryComposer {

    private final Ports.Ordering ordering;
    private final Ports.Catalog catalog;
    private final Ports.Delivery delivery;
    private final Duration enrichmentBudget;

    public OrderHistoryComposer(Ports.Ordering ordering, Ports.Catalog catalog, Ports.Delivery delivery,
                                Duration enrichmentBudget) {
        this.ordering = ordering;
        this.catalog = catalog;
        this.delivery = delivery;
        this.enrichmentBudget = enrichmentBudget;
    }

    public OrderHistory history(String customerId, int limit) {
        List<OrderRow> orders = ordering.recentOrders(customerId, Math.min(limit, 50));   // required
        if (orders.isEmpty()) {
            return new OrderHistory(customerId, List.of(), List.of());
        }
        Set<String> restaurantIds = new LinkedHashSet<>();
        List<String> orderIds = new ArrayList<>();
        orders.forEach(o -> {
            restaurantIds.add(o.restaurantId());
            orderIds.add(o.orderId());
        });

        List<String> degraded = new ArrayList<>();
        Map<String, RestaurantInfo> restaurants;
        Map<String, DeliveryInfo> deliveries;
        long deadline = System.nanoTime() + enrichmentBudget.toNanos();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Map<String, RestaurantInfo>> r = executor.submit(() -> catalog.restaurants(restaurantIds));
            Future<Map<String, DeliveryInfo>> d = executor.submit(() -> delivery.deliveries(orderIds));
            restaurants = orEmpty("catalog", r, deadline, degraded);
            deliveries = orEmpty("delivery", d, deadline, degraded);
        }

        List<OrderHistoryEntry> entries = new ArrayList<>(orders.size());
        for (OrderRow o : orders) {                                 // keep the driver's order
            RestaurantInfo restaurant = restaurants.get(o.restaurantId());
            DeliveryInfo d = deliveries.get(o.orderId());
            entries.add(new OrderHistoryEntry(o.orderId(), o.placedAt(), o.status(), o.totalPaise(),
                    restaurant == null ? null : restaurant.name(),
                    restaurant == null ? null : restaurant.area(),
                    d == null ? null : d.riderName(),
                    d == null ? null : d.deliveredAt()));
        }
        return new OrderHistory(customerId, List.copyOf(entries), List.copyOf(degraded));
    }

    private static <K, V> Map<K, V> orEmpty(String source, Future<Map<K, V>> future, long deadline, List<String> degraded) {
        try {
            return future.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
        } catch (ExecutionException | TimeoutException e) {
            future.cancel(true);
            degraded.add(source);
            return Map.of();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            degraded.add(source);
            return Map.of();
        }
    }
}
