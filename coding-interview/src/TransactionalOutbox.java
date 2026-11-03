import java.util.*;
import java.util.function.Consumer;

/** Problem: save an order AND publish OrderPlaced without dual-write inconsistency (DB commit + broker publish). */
public class TransactionalOutbox {

    record Order(String id, long cents) { }
    record OutboxRow(long seq, String aggregateId, String type, String payload) { }

    /** A toy "database" with atomic transactions: either all writes in tx are applied, or none. */
    static final class Database {
        final Map<String, Order> orders = new LinkedHashMap<>();
        final List<OutboxRow> outbox = new ArrayList<>();
        long lastPublishedSeq = 0;
        private long seq = 0;

        synchronized void inTransaction(Consumer<Database> work, boolean crashBeforeCommit) {
            Map<String, Order> ordersBackup = new LinkedHashMap<>(orders);
            List<OutboxRow> outboxBackup = new ArrayList<>(outbox);
            long seqBackup = seq;
            work.accept(this);
            if (crashBeforeCommit) {                       // rollback: nothing is visible, including the event
                orders.clear(); orders.putAll(ordersBackup);
                outbox.clear(); outbox.addAll(outboxBackup);
                seq = seqBackup;
                throw new IllegalStateException("crash before commit");
            }
        }

        void insertOutbox(String aggregateId, String type, String payload) { outbox.add(new OutboxRow(++seq, aggregateId, type, payload)); }
    }

    static final class OrderService {
        private final Database db;
        OrderService(Database db) { this.db = db; }

        void placeOrder(Order o, boolean crash) {
            db.inTransaction(tx -> {
                tx.orders.put(o.id(), o);                                                   // business write
                tx.insertOutbox(o.id(), "OrderPlaced", "{\"id\":\"" + o.id() + "\",\"cents\":" + o.cents() + "}"); // event write, SAME tx
            }, crash);
        }
    }

    /** Relay (poller or CDC like Debezium): reads unpublished rows in order, publishes, then advances the checkpoint. */
    static final class OutboxRelay {
        private final Database db;
        private final List<String> broker;
        OutboxRelay(Database db, List<String> broker) { this.db = db; this.broker = broker; }

        int publishPending(boolean crashAfterPublishBeforeCheckpoint) {
            int n = 0;
            for (OutboxRow row : db.outbox) {
                if (row.seq() <= db.lastPublishedSeq) continue;
                broker.add(row.type() + " " + row.payload() + " seq=" + row.seq());
                n++;
                if (crashAfterPublishBeforeCheckpoint) return n;                           // checkpoint NOT saved -> duplicate later
                db.lastPublishedSeq = row.seq();
            }
            return n;
        }
    }

    public static void main(String[] args) {
        Database db = new Database();
        List<String> broker = new ArrayList<>();
        OrderService orders = new OrderService(db);
        OutboxRelay relay = new OutboxRelay(db, broker);

        orders.placeOrder(new Order("A-1", 500), false);
        try { orders.placeOrder(new Order("A-2", 700), true); } catch (IllegalStateException e) { System.out.println("A-2: " + e.getMessage() + " -> no order, no event"); }
        orders.placeOrder(new Order("A-3", 900), false);
        check(db.orders.keySet().equals(Set.of("A-1", "A-3")) && db.outbox.size() == 2, "state and outbox always agree");

        relay.publishPending(true);                         // publishes A-1 then crashes before checkpoint
        relay.publishPending(false);                        // restarts: re-publishes A-1, then A-3
        broker.forEach(m -> System.out.println("broker <- " + m));
        System.out.println("orders in DB = " + db.orders.keySet() + ", outbox checkpoint = " + db.lastPublishedSeq);

        Set<String> processed = new LinkedHashSet<>();
        for (String m : broker) processed.add(m.substring(m.indexOf("seq=")));   // consumers dedupe by event id/seq
        System.out.println("consumer after dedupe = " + processed);
        check(broker.size() == 3 && processed.size() == 2, "at-least-once delivery, exactly-once effect with dedupe");
        System.out.println("PASS");
    }

    static void check(boolean ok, String what) { if (!ok) { System.out.println("FAILED: " + what); System.exit(1); } }
}
