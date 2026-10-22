package dev.foodfleet.ch30.orders;

import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Placing an order is one local transaction: the order (PENDING) and an outbox row. The request never waits
 * for payment-service, so it is not slowed or blocked by it: in the chapter's experiment orders kept being
 * accepted at full rate while the payments database was down, and all were confirmed within 10.2 s of being
 * placed once it returned.
 */
@Service
public class OrderService {

    static final String INSERT_ORDER = """
            INSERT INTO orders.orders (customer_id, item_id, amount_paise, status)
            VALUES (:customer, :item, :amount, 'PENDING')
            RETURNING id""";

    static final String INSERT_OUTBOX = """
            INSERT INTO orders.outbox (order_id, customer_id, item_id, amount_paise)
            VALUES (:order, :customer, :item, :amount)""";

    private final JdbcClient jdbc;
    private final TransactionTemplate tx;

    public OrderService(JdbcClient jdbc, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.tx = tx;
    }

    public OrderView place(PlaceOrder order) {
        return tx.execute(status -> {
            long id = jdbc.sql(INSERT_ORDER).param("customer", order.customerId()).param("item", order.itemId())
                    .param("amount", order.amountPaise()).query(Long.class).single();
            jdbc.sql(INSERT_OUTBOX).param("order", id).param("customer", order.customerId())
                    .param("item", order.itemId()).param("amount", order.amountPaise()).update();
            return new OrderView(id, "PENDING", null);
        });
    }

    public Optional<OrderView> find(long id) {
        return jdbc.sql("SELECT id, status, reason FROM orders.orders WHERE id = :id").param("id", id)
                .query((rs, n) -> new OrderView(rs.getLong(1), rs.getString(2), rs.getString(3)))
                .optional();
    }
}
