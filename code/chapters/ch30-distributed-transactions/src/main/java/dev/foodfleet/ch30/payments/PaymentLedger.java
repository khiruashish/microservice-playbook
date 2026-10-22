package dev.foodfleet.ch30.payments;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * payment-service's side: debits wallets and takes portions from stock, idempotently. A batch is one local
 * transaction (an insert of the debit rows that skips orders already decided, then one update per dish and per
 * customer); if a constraint refuses the batch (a wallet or a dish would go below zero), the orders are applied
 * one by one so only the ones that can't be served are rejected. Every decision is stored, so a redelivered
 * order gets the same answer.
 */
@Component
public class PaymentLedger implements PaymentsPort {

    static final String INSERT_DEBITS = """
            INSERT INTO payments.debits (order_id, customer_id, item_id, amount_paise, status)
            SELECT order_id, customer_id, item_id, amount_paise, 'DEBITED'
              FROM jsonb_to_recordset(CAST(:orders AS jsonb))
                   AS o(order_id bigint, customer_id bigint, item_id bigint, amount_paise int)
            ON CONFLICT (order_id) DO NOTHING
            RETURNING order_id, customer_id, item_id, amount_paise""";

    static final String TAKE_STOCK = """
            WITH d AS (SELECT * FROM jsonb_to_recordset(CAST(:items AS jsonb)) AS d(item_id bigint, n int)),
                 locked AS (SELECT s.item_id FROM payments.stock s JOIN d USING (item_id) ORDER BY s.item_id FOR UPDATE)
            UPDATE payments.stock s SET available = s.available - d.n
              FROM d, locked WHERE s.item_id = d.item_id AND locked.item_id = d.item_id""";

    static final String DEBIT_WALLETS = """
            WITH d AS (SELECT * FROM jsonb_to_recordset(CAST(:customers AS jsonb)) AS d(customer_id bigint, amount bigint)),
                 locked AS (SELECT w.customer_id FROM payments.wallets w JOIN d USING (customer_id) ORDER BY w.customer_id FOR UPDATE)
            UPDATE payments.wallets w SET balance_paise = w.balance_paise - d.amount
              FROM d, locked WHERE w.customer_id = d.customer_id AND locked.customer_id = d.customer_id""";

    static final String DECIDED = """
            SELECT order_id, status, reason FROM payments.debits
             WHERE order_id IN (SELECT (o->>'order_id')::bigint FROM jsonb_array_elements(CAST(:orders AS jsonb)) o)""";

    private final JdbcClient jdbc;
    private final TransactionTemplate tx;

    public PaymentLedger(JdbcClient jdbc, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.tx = tx;
    }

    @Override
    public List<Decision> apply(List<OrderPlaced> batch) {
        if (batch.isEmpty()) {
            return List.of();
        }
        try {
            tx.executeWithoutResult(status -> {
                List<OrderPlaced> fresh = jdbc.sql(INSERT_DEBITS).param("orders", Batches.orders(batch))
                        .query((rs, n) -> new OrderPlaced(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getInt(4)))
                        .list();
                if (!fresh.isEmpty()) {
                    jdbc.sql(TAKE_STOCK).param("items", Batches.items(Batches.portionsPerItem(fresh))).update();
                    jdbc.sql(DEBIT_WALLETS).param("customers", Batches.customers(Batches.paisePerCustomer(fresh))).update();
                }
            });
        } catch (DataIntegrityViolationException someoneCannotPay) {
            batch.forEach(this::applyOne);
        }
        return decisions(batch);
    }

    /** One order in its own transaction; a refusal is recorded so it is never re-evaluated. */
    void applyOne(OrderPlaced o) {
        Optional<String> refusal = tx.execute(status -> {
            boolean fresh = !jdbc.sql(INSERT_DEBITS).param("orders", Batches.orders(List.of(o)))
                    .query((rs, n) -> rs.getLong(1)).list().isEmpty();
            if (!fresh) {
                return Optional.<String>empty();
            }
            int portion = jdbc.sql("UPDATE payments.stock SET available = available - 1 WHERE item_id = :item AND available >= 1")
                    .param("item", o.itemId()).update();
            int paid = portion == 0 ? 0 : jdbc.sql("""
                    UPDATE payments.wallets SET balance_paise = balance_paise - :amount
                     WHERE customer_id = :customer AND balance_paise >= :amount""")
                    .param("amount", o.amountPaise()).param("customer", o.customerId()).update();
            if (portion == 1 && paid == 1) {
                return Optional.<String>empty();
            }
            status.setRollbackOnly();
            return Optional.of(portion == 0 ? "SOLD_OUT" : "INSUFFICIENT_FUNDS");
        });
        refusal.ifPresent(reason -> jdbc.sql("""
                INSERT INTO payments.debits (order_id, customer_id, item_id, amount_paise, status, reason)
                VALUES (:order, :customer, :item, :amount, 'REJECTED', :reason)
                ON CONFLICT (order_id) DO NOTHING""")
                .param("order", o.orderId()).param("customer", o.customerId()).param("item", o.itemId())
                .param("amount", o.amountPaise()).param("reason", reason).update());
    }

    private List<Decision> decisions(List<OrderPlaced> batch) {
        Map<Long, Decision> stored = new HashMap<>();
        jdbc.sql(DECIDED).param("orders", Batches.orders(batch))
                .query((rs, n) -> new Decision(rs.getLong(1), "DEBITED".equals(rs.getString(2)), rs.getString(3)))
                .list().forEach(d -> stored.put(d.orderId(), d));
        List<Decision> out = new ArrayList<>(batch.size());
        for (OrderPlaced o : batch) {
            Decision d = stored.get(o.orderId());
            if (d == null) {
                throw new IllegalStateException("no decision stored for order " + o.orderId());
            }
            out.add(d);
        }
        return out;
    }
}
