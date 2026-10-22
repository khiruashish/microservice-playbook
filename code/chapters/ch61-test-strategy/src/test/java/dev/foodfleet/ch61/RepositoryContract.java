package dev.foodfleet.ch61;

import dev.foodfleet.ch61.order.JdbcOrderRepository;
import dev.foodfleet.ch61.order.Order;
import dev.foodfleet.ch61.order.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The repository's tests, run against a real database by each subclass. */
abstract class RepositoryContract {

    JdbcOrderRepository repo;

    abstract JdbcOrderRepository.Connections database() throws Exception;

    @BeforeEach
    void freshDatabase() throws Exception {
        repo = new JdbcOrderRepository(database());
    }

    static Order order(String key, String customer, int qty) {
        return new Order(0, key, customer, 7, List.of(new Order.Line("paneer-tikka", qty, new BigDecimal("129.50")), new Order.Line("naan", 2,
                new BigDecimal("45.00"))), new BigDecimal("349.00"), new BigDecimal("69.80"), new BigDecimal("13.96"), new BigDecimal("293.16"), "PLACED");
    }

    @Test
    void storesAndReadsBackEveryField() {
        Order o = repo.insertIfAbsent(order("k1", "c1", 2)).order();
        Order read = repo.findById(o.id()).orElseThrow();
        assertEquals(new BigDecimal("349.00"), read.subtotal());
        assertEquals(new BigDecimal("69.80"), read.discount());
        assertEquals(new BigDecimal("13.96"), read.tax());
        assertEquals(new BigDecimal("293.16"), read.total());
        assertEquals(2, read.lines().size());
        assertEquals("c1", read.customerId());
    }

    @Test
    void theSameIdempotencyKeyStoresOneOrder() {
        OrderRepository.Stored first = repo.insertIfAbsent(order("k1", "c1", 2));
        OrderRepository.Stored again = repo.insertIfAbsent(order("k1", "c1", 2));
        assertTrue(first.created());
        assertFalse(again.created());
        assertEquals(first.order().id(), again.order().id());
        assertEquals(1, repo.countForRestaurant(7));
    }

    @Test
    void recentOrdersComeNewestFirst() {
        long a = repo.insertIfAbsent(order("k1", "c1", 1)).order().id();
        long b = repo.insertIfAbsent(order("k2", "c1", 1)).order().id();
        repo.insertIfAbsent(order("k3", "c2", 1));
        List<Order> recent = repo.recent("c1", 10);
        assertEquals(List.of(b, a), recent.stream().map(Order::id).toList());
    }

    @Test
    void aFailedLineLeavesNoHalfOrder() {
        // quantity 25 breaks the table's check constraint: the header insert must be rolled back with it
        assertThrows(IllegalStateException.class, () -> repo.insertIfAbsent(order("k1", "c1", 25)));
        assertEquals(0, repo.countForRestaurant(7));
    }
}
