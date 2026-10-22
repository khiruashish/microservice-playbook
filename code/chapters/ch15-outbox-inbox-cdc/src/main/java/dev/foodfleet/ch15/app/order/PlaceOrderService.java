package dev.foodfleet.ch15.app.order;

import dev.foodfleet.ch15.app.outbox.Outbox;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Places an order. The INSERT into {@code orders} and the INSERT into {@code outbox} are in one
 * local transaction: both commit or neither does. Nothing here talks to the broker.
 */
@Service
public class PlaceOrderService {

    private final JdbcClient jdbc;
    private final Outbox outbox;

    public PlaceOrderService(JdbcClient jdbc, Outbox outbox) {
        this.jdbc = jdbc;
        this.outbox = outbox;
    }

    @Transactional
    public void place(String orderId, String customerId, long totalPaise) {
        jdbc.sql("insert into orders (id, customer_id, total_paise, status) values (:id, :customer, :total, 'PLACED')")
                .param("id", orderId)
                .param("customer", customerId)
                .param("total", totalPaise)
                .update();
        outbox.append("order", orderId, "OrderPlaced",
                new OrderPlaced(orderId, customerId, totalPaise, totalPaise / 1_000));
    }
}
