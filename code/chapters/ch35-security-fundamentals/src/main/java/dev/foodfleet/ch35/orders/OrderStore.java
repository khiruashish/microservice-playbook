package dev.foodfleet.ch35.orders;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/** In memory, so this chapter's examples need no database; chapters 29-34 cover persistence. */
public class OrderStore {

    private final ConcurrentHashMap<Long, Order> orders = new ConcurrentHashMap<>();

    public void save(Order order) {
        orders.put(order.id(), order);
    }

    public Optional<Order> find(long id) {
        return Optional.ofNullable(orders.get(id));
    }

    public List<Order> page(Predicate<Order> filter, int offset, int size) {
        return orders.values().stream().filter(filter).sorted(Comparator.comparingLong(Order::id))
                .skip(offset).limit(size).toList();
    }

    public int size() {
        return orders.size();
    }
}
