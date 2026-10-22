package dev.foodfleet.ch15.outbox;

import dev.foodfleet.ch15.orders.OrderEvents;
import dev.foodfleet.ch15.orders.OrderRow;
import dev.foodfleet.ch15.orders.OrderService;
import dev.foodfleet.ch15.sim.Database;
import dev.foodfleet.ch15.sim.Faults;

/**
 * The transactional outbox: the order change and the event to publish are written in ONE local
 * transaction. Either both are committed or neither is; publishing is someone else's job (a
 * {@link PollingPublisher} or a {@link LogTailingRelay}), done later and retried until it succeeds.
 *
 * <p>With {@code deleteAfterInsert} the outbox row is inserted and deleted in the same transaction:
 * the table stays empty, yet the INSERT is in the write-ahead log for a CDC relay to read.
 */
public final class OutboxOrderService implements OrderService {

    private final Database db;
    private final Faults faults;
    private final boolean deleteAfterInsert;

    public OutboxOrderService(Database db, Faults faults, boolean deleteAfterInsert) {
        this.db = db;
        this.faults = faults;
        this.deleteAfterInsert = deleteAfterInsert;
    }

    @Override
    public void place(String orderId, String customerId, long totalPaise) {
        OrderRow row = new OrderRow(orderId, customerId, totalPaise, OrderRow.Status.PLACED);
        db.inTransaction(tx -> {
            tx.insert(OrderRow.TABLE, orderId, row);
            append(tx, orderId, OrderEvents.PLACED, OrderEvents.placedPayload(row));
            faults.maybeCrash("service.commit-fails");    // rolls back the order AND its event
            return null;
        });
    }

    @Override
    public void cancel(String orderId) {
        db.inTransaction(tx -> {
            OrderRow row = (OrderRow) tx.get(OrderRow.TABLE, orderId)
                    .orElseThrow(() -> new IllegalArgumentException("unknown order " + orderId));
            tx.update(OrderRow.TABLE, orderId, row.cancelled());
            append(tx, orderId, OrderEvents.CANCELLED, OrderEvents.cancelledPayload(row));
            faults.maybeCrash("service.commit-fails");
            return null;
        });
    }

    private void append(Database.Tx tx, String orderId, String type, String payload) {
        OutboxRow row = new OutboxRow(db.nextSequence(), OrderEvents.messageId(orderId, type), orderId, type,
                payload, false);
        tx.insert(OutboxRow.TABLE, row.key(), row);
        if (deleteAfterInsert) {
            tx.delete(OutboxRow.TABLE, row.key());
        }
    }
}
