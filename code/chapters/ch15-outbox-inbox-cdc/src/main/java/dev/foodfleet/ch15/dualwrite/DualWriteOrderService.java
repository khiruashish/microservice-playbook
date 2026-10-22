package dev.foodfleet.ch15.dualwrite;

import dev.foodfleet.ch15.orders.OrderEvents;
import dev.foodfleet.ch15.orders.OrderRow;
import dev.foodfleet.ch15.orders.OrderService;
import dev.foodfleet.ch15.sim.Broker;
import dev.foodfleet.ch15.sim.Database;
import dev.foodfleet.ch15.sim.Faults;
import dev.foodfleet.ch15.sim.Message;

/**
 * The dual write this chapter replaces: the service writes to its database AND publishes to the
 * broker, two systems with no shared transaction. Whichever goes first, a failure between the two
 * leaves them disagreeing.
 * <ul>
 *   <li>{@link Order#DATABASE_FIRST}: commit, then publish. A crash or broker error after the commit
 *       means the order exists and nobody is told: a <em>lost event</em>.</li>
 *   <li>{@link Order#BROKER_FIRST}: publish, then commit. If the commit fails, everyone was told about
 *       an order that doesn't exist: a <em>phantom event</em>.</li>
 * </ul>
 */
public final class DualWriteOrderService implements OrderService {

    public enum Order { DATABASE_FIRST, BROKER_FIRST }

    private final Database db;
    private final Broker broker;
    private final Order order;
    private final Faults faults;

    public DualWriteOrderService(Database db, Broker broker, Order order, Faults faults) {
        this.db = db;
        this.broker = broker;
        this.order = order;
        this.faults = faults;
    }

    @Override
    public void place(String orderId, String customerId, long totalPaise) throws Exception {
        OrderRow row = new OrderRow(orderId, customerId, totalPaise, OrderRow.Status.PLACED);
        Message event = new Message(OrderEvents.messageId(orderId, OrderEvents.PLACED), orderId,
                OrderEvents.PLACED, OrderEvents.placedPayload(row));
        write(event, tx -> tx.insert(OrderRow.TABLE, orderId, row));
    }

    @Override
    public void cancel(String orderId) throws Exception {
        OrderRow row = (OrderRow) db.table(OrderRow.TABLE).get(orderId);
        if (row == null) {
            throw new IllegalArgumentException("unknown order " + orderId);
        }
        Message event = new Message(OrderEvents.messageId(orderId, OrderEvents.CANCELLED), orderId,
                OrderEvents.CANCELLED, OrderEvents.cancelledPayload(row));
        write(event, tx -> tx.update(OrderRow.TABLE, orderId, row.cancelled()));
    }

    private void write(Message event, java.util.function.Consumer<Database.Tx> change) throws Exception {
        if (order == Order.DATABASE_FIRST) {
            db.inTransaction(tx -> {
                change.accept(tx);
                return null;
            });
            faults.maybeCrash("service.after-commit");   // pod killed here: the event is never sent
            broker.publish(event);                       // broker down here: same result
        } else {
            broker.publish(event);
            db.inTransaction(tx -> {
                change.accept(tx);
                faults.maybeCrash("service.commit-fails"); // constraint, deadlock, failover: rolled back
                return null;
            });
        }
    }
}
