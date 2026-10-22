package dev.foodfleet.ch21.api;

import dev.foodfleet.ch21.orders.OrderStore;
import dev.foodfleet.ch21.orders.OrderSummary;
import dev.foodfleet.ch21.paging.Cursor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** The same contract as JdbcOrderStore, in memory, for web-layer tests. */
class InMemoryOrderStore implements OrderStore {

    private static final Comparator<OrderSummary> NEWEST_FIRST = Comparator.comparing(OrderSummary::placedAt)
            .thenComparing(OrderSummary::id).reversed();

    private final Map<String, OrderSummary> orders = new ConcurrentHashMap<>();

    void put(OrderSummary order) {
        orders.put(order.id(), order);
    }

    @Override
    public List<OrderSummary> page(String customerId, Optional<Cursor> after, int limit) {
        List<OrderSummary> mine = new ArrayList<>(orders.values().stream()
                .filter(o -> o.customerId().equals(customerId)).sorted(NEWEST_FIRST).toList());
        if (after.isPresent()) {
            OrderSummary marker = new OrderSummary(after.get().orderId(), customerId, "", 0, "",
                    after.get().placedAt(), null, 0);
            mine.removeIf(o -> NEWEST_FIRST.compare(o, marker) <= 0);
        }
        return mine.stream().limit(limit).toList();
    }

    @Override
    public Optional<OrderSummary> find(String orderId) {
        return Optional.ofNullable(orders.get(orderId));
    }

    @Override
    public OrderSummary updateDeliveryInstructions(String orderId, long expectedVersion, String instructions) {
        OrderSummary current = find(orderId).orElseThrow(() -> new OrderNotFound(orderId));
        if (current.version() != expectedVersion) {
            throw new VersionConflict(orderId, current.version());
        }
        OrderSummary updated = new OrderSummary(current.id(), current.customerId(), current.restaurant(),
                current.totalPaise(), current.status(), current.placedAt(), instructions, current.version() + 1);
        orders.put(orderId, updated);
        return updated;
    }
}
