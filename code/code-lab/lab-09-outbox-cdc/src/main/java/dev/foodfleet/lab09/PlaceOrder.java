package dev.foodfleet.lab09;

import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * The fix for lab 07's dual write: the order and its event are two rows in one local transaction. Either both commit
 * or neither does; publishing happens later, from the outbox, and can be retried until it succeeds.
 */
@Service
public class PlaceOrder {

    public record OrderPlaced(String orderId, String customerId, long totalPaise) {
    }

    private final JdbcClient db;
    private final JsonMapper json;

    public PlaceOrder(JdbcClient db, JsonMapper json) {
        this.db = db;
        this.json = json;
    }

    @Transactional
    public String place(String customerId, long totalPaise) {
        String orderId = "o-" + UUID.randomUUID();
        db.sql("INSERT INTO orders (id, customer_id, total_paise, status) VALUES (?, ?, ?, 'PLACED')")
                .params(orderId, customerId, totalPaise).update();
        db.sql("""
                INSERT INTO outbox (id, aggregate_type, aggregate_id, type, payload)
                VALUES (?, 'order', ?, 'OrderPlaced', ?::jsonb)
                """)
                .params(UUID.randomUUID(), orderId, json.writeValueAsString(new OrderPlaced(orderId, customerId, totalPaise)))
                .update();
        return orderId;
    }
}
