package dev.foodfleet.ch30.orders;

import dev.foodfleet.ch30.payments.Decision;
import dev.foodfleet.ch30.payments.OrderPlaced;
import dev.foodfleet.ch30.payments.PaymentsPort;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The saga's forward path, driven by the outbox. In one order-service transaction it claims up to 20 outbox
 * rows, asks payments for decisions, records them on the orders and deletes the rows. If payments fails or this
 * instance dies, the transaction rolls back and the rows are claimed again: payments answers repeated orders
 * with the decision it already stored, so nothing is charged twice. Several instances can relay at once
 * (SKIP LOCKED gives each different rows).
 */
@Component
public class OrderRelay {

    private static final Logger log = LoggerFactory.getLogger(OrderRelay.class);
    static final int BATCH = 20;

    static final String CLAIM = """
            DELETE FROM orders.outbox
             WHERE id IN (SELECT id FROM orders.outbox ORDER BY id LIMIT :batch FOR UPDATE SKIP LOCKED)
            RETURNING order_id, customer_id, item_id, amount_paise""";

    static final String DECIDE = """
            UPDATE orders.orders SET status = :status, reason = :reason, decided_at = clock_timestamp()
             WHERE id = :id AND status = 'PENDING'""";

    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final PaymentsPort payments;

    public OrderRelay(JdbcClient jdbc, TransactionTemplate tx, PaymentsPort payments) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.payments = payments;
    }

    @Scheduled(fixedDelayString = "${orders.relay.poll-interval:20ms}")
    public void poll() {
        try {
            while (relayOnce() == BATCH) {
                // a full batch: there may be more waiting
            }
        } catch (RuntimeException e) {
            log.warn("order relay paused, will retry: {}", e.toString());
        }
    }

    /** Relays one batch; returns how many orders it decided. */
    public int relayOnce() {
        Integer decided = tx.execute(status -> {
            List<OrderPlaced> batch = jdbc.sql(CLAIM).param("batch", BATCH)
                    .query((rs, n) -> new OrderPlaced(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getInt(4)))
                    .list().stream().sorted(Comparator.comparingLong(OrderPlaced::orderId)).toList();   // first come, first served
            if (batch.isEmpty()) {
                return 0;
            }
            for (Decision d : payments.apply(batch)) {
                jdbc.sql(DECIDE).param("status", d.debited() ? "CONFIRMED" : "REJECTED").param("reason", d.reason())
                        .param("id", d.orderId()).update();
            }
            return batch.size();
        });
        return decided == null ? 0 : decided;
    }
}
