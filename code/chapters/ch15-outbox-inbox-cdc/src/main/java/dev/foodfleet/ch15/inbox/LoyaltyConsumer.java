package dev.foodfleet.ch15.inbox;

import dev.foodfleet.ch15.orders.OrderEvents;
import dev.foodfleet.ch15.sim.Broker;
import dev.foodfleet.ch15.sim.Database;
import dev.foodfleet.ch15.sim.Faults;
import dev.foodfleet.ch15.sim.Message;
import java.util.Map;

/**
 * loyalty-service: +points on OrderPlaced, -points on OrderCancelled, in its own database.
 *
 * <p>Delivery is at-least-once (publisher retries, relay restarts, consumer restarts), so the same
 * message arrives more than once. Two versions:
 * <ul>
 *   <li><b>naive</b>: apply the effect, then commit the offset. A duplicate applies it again.</li>
 *   <li><b>inbox</b>: in the SAME local transaction as the effect, insert the message id into an
 *       {@code inbox} table with a unique key. A duplicate fails the insert and is skipped. Effect and
 *       "seen" record commit together, so a crash can't separate them.</li>
 * </ul>
 */
public final class LoyaltyConsumer {

    public static final String POINTS = "points";
    public static final String INBOX = "inbox";

    private final Database db;
    private final Broker.Subscription subscription;
    private final Faults faults;
    private final boolean useInbox;
    private int duplicatesSkipped;

    public LoyaltyConsumer(Database db, Broker broker, Faults faults, boolean useInbox) {
        this.db = db;
        this.subscription = broker.subscribe("loyalty-service");
        this.faults = faults;
        this.useInbox = useInbox;
    }

    /** Handles up to {@code max} messages. */
    public int poll(int max) {
        int handled = 0;
        Message message;
        while (handled < max && (message = subscription.poll()) != null) {
            handle(message);
            faults.maybeCrash("consumer.before-offset-commit");  // effect committed, offset not
            subscription.commit();
            handled++;
        }
        return handled;
    }

    private void handle(Message message) {
        Map<String, String> payload = OrderEvents.parse(message.payload());
        long delta = Long.parseLong(payload.get("points")) * (message.type().equals(OrderEvents.CANCELLED) ? -1 : 1);
        String customer = payload.get("customer");
        try {
            db.inTransaction(tx -> {
                if (useInbox) {
                    tx.insert(INBOX, message.id(), message.type());       // unique: a duplicate throws
                }
                tx.get(POINTS, customer).ifPresentOrElse(
                        current -> tx.update(POINTS, customer, (Long) current + delta),
                        () -> tx.insert(POINTS, customer, delta));
                return null;
            });
        } catch (Database.DuplicateKey alreadyProcessed) {
            duplicatesSkipped++;                                       // seen before: nothing to do
        }
    }

    public void restart() {
        subscription.restart();
    }

    public boolean caughtUp() {
        return subscription.caughtUp();
    }

    public int duplicatesSkipped() {
        return duplicatesSkipped;
    }

    public long totalPoints() {
        return db.table(POINTS).values().stream().mapToLong(Long.class::cast).sum();
    }
}
