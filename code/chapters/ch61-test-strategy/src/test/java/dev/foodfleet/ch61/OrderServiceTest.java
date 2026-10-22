package dev.foodfleet.ch61;

import dev.foodfleet.ch61.order.MenuClient;
import dev.foodfleet.ch61.order.Order;
import dev.foodfleet.ch61.order.OrderRepository;
import dev.foodfleet.ch61.order.OrderService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Unit tests: OrderService with test doubles for menu-service and the database. */
@Tag("unit")
class OrderServiceTest {

    /** A fake repository: a map, with the idempotency rule implemented the way we believe the SQL does it. */
    static final class InMemoryOrders implements OrderRepository {
        final Map<String, Order> byKey = new HashMap<>();
        long next = 1;

        public Stored insertIfAbsent(Order o) {
            Order existing = byKey.get(o.idempotencyKey());
            if (existing != null) {
                return new Stored(existing, false);
            }
            Order stored = new Order(next++, o.idempotencyKey(), o.customerId(), o.restaurantId(), o.lines(), o.subtotal(), o.discount(), o.tax(),
                    o.total(), o.status());
            byKey.put(o.idempotencyKey(), stored);
            return new Stored(stored, true);
        }

        public Optional<Order> findById(long id) {
            return byKey.values().stream().filter(o -> o.id() == id).findFirst();
        }

        public List<Order> recent(String customerId, int limit) {
            List<Order> l = new ArrayList<>(byKey.values());
            l.sort((a, b) -> Long.compare(b.id(), a.id()));
            return l.stream().filter(o -> o.customerId().equals(customerId)).limit(limit).toList();
        }

        public int countForRestaurant(int restaurantId) {
            return (int) byKey.values().stream().filter(o -> o.restaurantId() == restaurantId).count();
        }
    }

    static final MenuClient MENU = (restaurant, item) -> switch (item) {
        case "paneer-tikka" -> new MenuClient.Price(new BigDecimal("129.50"), true);
        case "kulfi" -> new MenuClient.Price(new BigDecimal("79.00"), false);
        default -> new MenuClient.Price(BigDecimal.ZERO, false);
    };

    static Order.Request request(String key, int quantity, String item) {
        return new Order.Request(key, "c1", 7, List.of(new Order.Item(item, quantity)), "FIRST20");
    }

    @Test
    void placesAnOrderWithTodaysPrices() {
        OrderService s = new OrderService(MENU, new InMemoryOrders());
        Order o = s.place(request("k1", 2, "paneer-tikka")).order();
        assertEquals(new BigDecimal("259.00"), o.subtotal());
        assertEquals(new BigDecimal("51.80"), o.discount());
        assertEquals(new BigDecimal("217.56"), o.total());
    }

    @Test
    void aRetriedRequestReturnsTheSameOrder() {
        OrderService s = new OrderService(MENU, new InMemoryOrders());
        OrderRepository.Stored first = s.place(request("k1", 2, "paneer-tikka"));
        OrderRepository.Stored again = s.place(request("k1", 2, "paneer-tikka"));
        assertTrue(first.created());
        assertFalse(again.created());
        assertEquals(first.order().id(), again.order().id());
    }

    @Test
    void quantitiesMustBeOneToTwenty() {
        OrderService s = new OrderService(MENU, new InMemoryOrders());
        assertThrows(IllegalArgumentException.class, () -> s.place(request("k1", 0, "paneer-tikka")));
        assertThrows(IllegalArgumentException.class, () -> s.place(request("k2", 21, "paneer-tikka")));
        assertEquals(20, s.place(request("k3", 20, "paneer-tikka")).order().lines().getFirst().quantity());
    }

    @Test
    void soldOutItemsAreRefused() {
        OrderService s = new OrderService(MENU, new InMemoryOrders());
        assertThrows(IllegalArgumentException.class, () -> s.place(request("k1", 3, "kulfi")));
    }
}
