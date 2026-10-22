package dev.foodfleet.ch15.simulation;

import dev.foodfleet.ch15.dualwrite.DualWriteOrderService;
import dev.foodfleet.ch15.inbox.LoyaltyConsumer;
import dev.foodfleet.ch15.orders.OrderEvents;
import dev.foodfleet.ch15.orders.OrderRow;
import dev.foodfleet.ch15.orders.OrderService;
import dev.foodfleet.ch15.outbox.LogTailingRelay;
import dev.foodfleet.ch15.outbox.OutboxOrderService;
import dev.foodfleet.ch15.outbox.OutboxRow;
import dev.foodfleet.ch15.outbox.PollingPublisher;
import dev.foodfleet.ch15.sim.Broker;
import dev.foodfleet.ch15.sim.Crash;
import dev.foodfleet.ch15.sim.Database;
import dev.foodfleet.ch15.sim.Faults;
import dev.foodfleet.ch15.sim.Message;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * ordering-service publishes OrderPlaced/OrderCancelled; loyalty-service turns them into points.
 * The same 10,000 orders (10% later cancelled) run through five designs, with the same seeded
 * faults: crashes after a commit, failed commits, an unavailable broker, lost broker acks, relay
 * crashes and consumer crashes. At the end the faults stop, everything drains, and we count.
 */
public final class OutboxSimulation {

    public enum Design {
        DUAL_WRITE_DATABASE_FIRST("dual write: commit, then publish"),
        DUAL_WRITE_BROKER_FIRST("dual write: publish, then commit"),
        OUTBOX_POLLING_NO_INBOX("outbox + polling publisher, consumer without inbox"),
        OUTBOX_POLLING_INBOX("outbox + polling publisher + inbox"),
        OUTBOX_CDC_INBOX("outbox + log tailing (CDC) + inbox");

        public final String label;

        Design(String label) {
            this.label = label;
        }
    }

    public record Report(Design design, int ordersCommitted, int eventsExpected, int messagesOnBroker,
                         int lostEvents, int phantomEvents, int duplicateMessages, int duplicatesSkipped,
                         long pointsExpected, long pointsAwarded, int crashes, int outboxRowsLeft) {

        public long pointsWrong() {
            return pointsAwarded - pointsExpected;
        }

        public boolean consistent() {
            return lostEvents == 0 && phantomEvents == 0 && pointsWrong() == 0;
        }
    }

    /** Fault probabilities per named point (illustrative, but in the range real systems see). */
    public static Map<String, Double> foodFleetFaults() {
        Map<String, Double> odds = new HashMap<>();
        odds.put("service.after-commit", 0.005);          // pod killed between commit and publish
        odds.put("service.commit-fails", 0.01);           // deadlock, constraint, failover
        odds.put("broker.unavailable", 0.01);
        odds.put("broker.ack-lost", 0.005);
        odds.put("relay.after-publish", 0.003);
        odds.put("consumer.before-offset-commit", 0.005);
        return odds;
    }

    public static Report run(Design design, int orders, long seed) {
        Faults faults = new Faults(foodFleetFaults(), seed);
        Database orderDb = new Database("ordering");
        Database loyaltyDb = new Database("loyalty");
        Broker broker = new Broker(faults);

        OrderService service = switch (design) {
            case DUAL_WRITE_DATABASE_FIRST ->
                    new DualWriteOrderService(orderDb, broker, DualWriteOrderService.Order.DATABASE_FIRST, faults);
            case DUAL_WRITE_BROKER_FIRST ->
                    new DualWriteOrderService(orderDb, broker, DualWriteOrderService.Order.BROKER_FIRST, faults);
            case OUTBOX_POLLING_NO_INBOX, OUTBOX_POLLING_INBOX -> new OutboxOrderService(orderDb, faults, false);
            case OUTBOX_CDC_INBOX -> new OutboxOrderService(orderDb, faults, true);
        };
        Relay relay = switch (design) {
            case DUAL_WRITE_DATABASE_FIRST, DUAL_WRITE_BROKER_FIRST -> Relay.NONE;
            case OUTBOX_POLLING_NO_INBOX, OUTBOX_POLLING_INBOX -> Relay.of(new PollingPublisher(orderDb, broker, faults, 50));
            case OUTBOX_CDC_INBOX -> Relay.of(new LogTailingRelay(orderDb, broker, faults, 20));
        };
        LoyaltyConsumer loyalty = new LoyaltyConsumer(loyaltyDb, broker, faults,
                design != Design.OUTBOX_POLLING_NO_INBOX);

        Random random = new Random(seed + 1);
        int[] crashes = {0};
        for (int i = 0; i < orders; i++) {
            String orderId = "o-%05d".formatted(i);
            attempt(() -> service.place(orderId, "c-" + random.nextInt(500), 20_000 + random.nextInt(130_000)));
            if (random.nextDouble() < 0.10 && orderDb.table(OrderRow.TABLE).containsKey(orderId)) {
                attempt(() -> service.cancel(orderId));
            }
            if (i % 10 == 9) {
                pump(relay, loyalty, crashes);
            }
        }
        faults.heal();
        for (int round = 0; round < 10_000 && !(relay.drained() && loyalty.caughtUp()); round++) {
            pump(relay, loyalty, crashes);
        }
        return report(design, orderDb, broker, loyalty, crashes[0]);
    }

    private static void pump(Relay relay, LoyaltyConsumer loyalty, int[] crashes) {
        try {
            relay.poll();
        } catch (Crash crash) {
            crashes[0]++;
            relay.restart();
        }
        try {
            loyalty.poll(50);
        } catch (Crash crash) {
            crashes[0]++;
            loyalty.restart();
        }
    }

    private static void attempt(ThrowingRunnable action) {
        try {
            action.run();
        } catch (Exception failure) {     // includes Crash
            // The customer sees an error; whatever state is left behind is what we measure.
        }
    }

    private static Report report(Design design, Database orderDb, Broker broker, LoyaltyConsumer loyalty,
                                 int crashes) {
        Set<String> expected = new HashSet<>();
        long pointsExpected = 0;
        for (Object o : orderDb.table(OrderRow.TABLE).values()) {
            OrderRow order = (OrderRow) o;
            expected.add(OrderEvents.messageId(order.id(), OrderEvents.PLACED));
            if (order.status() == OrderRow.Status.CANCELLED) {
                expected.add(OrderEvents.messageId(order.id(), OrderEvents.CANCELLED));
            } else {
                pointsExpected += order.points();
            }
        }
        Set<String> published = new HashSet<>();
        for (Message m : broker.log()) {
            published.add(m.id());
        }
        int lost = (int) expected.stream().filter(id -> !published.contains(id)).count();
        int phantom = (int) published.stream().filter(id -> !expected.contains(id)).count();
        return new Report(design, orderDb.table(OrderRow.TABLE).size(), expected.size(), broker.log().size(),
                lost, phantom, broker.log().size() - published.size(), loyalty.duplicatesSkipped(),
                pointsExpected, loyalty.totalPoints(), crashes, orderDb.table(OutboxRow.TABLE).size());
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    /** The two relays behind one face, plus "no relay" for the dual-write designs. */
    private interface Relay {
        Relay NONE = new Relay() {
        };

        default void poll() {
        }

        default void restart() {
        }

        default boolean drained() {
            return true;
        }

        static Relay of(PollingPublisher publisher) {
            return new Relay() {
                public void poll() {
                    publisher.poll();
                }

                public boolean drained() {
                    return publisher.backlog() == 0;
                }
            };
        }

        static Relay of(LogTailingRelay cdc) {
            return new Relay() {
                public void poll() {
                    cdc.poll();
                }

                public void restart() {
                    cdc.restart();
                }

                public boolean drained() {
                    return cdc.lag() == 0;
                }
            };
        }
    }

    public static void main(String[] args) {
        System.out.println("10,000 orders per design (10% cancelled), same seeded faults\n");
        System.out.printf("%-50s %7s %7s %5s %7s %9s %9s %10s%n",
                "design", "events", "on bus", "lost", "phantom", "dup msgs", "dedup'd", "points off");
        for (Design design : Design.values()) {
            Report r = run(design, 10_000, 15);
            System.out.printf("%-50s %7d %7d %5d %7d %9d %9d %+10d%n", design.label, r.eventsExpected(),
                    r.messagesOnBroker(), r.lostEvents(), r.phantomEvents(), r.duplicateMessages(),
                    r.duplicatesSkipped(), r.pointsWrong());
        }
    }
}
