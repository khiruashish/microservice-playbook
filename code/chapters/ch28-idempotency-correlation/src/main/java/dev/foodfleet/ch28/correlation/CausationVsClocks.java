package dev.foodfleet.ch28.correlation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Rebuilding what happened to an order from events written by six services, each running three
 * instances whose clocks differ slightly (NTP: a few milliseconds), and in a second run with one
 * instance whose clock is 250 ms behind (it happens). Each order's flow:
 * <pre>
 * OrderPlaced -> PaymentCaptured -> KitchenAccepted -> RiderAssigned -> CustomerNotified
 *                      `-> LoyaltyPointsAdded
 * </pre>
 * Each effect really happens 2-60 ms after its cause. Ordering by timestamp is compared with
 * following causation ids; and finding an order's events by the {@code orderId} field (absent from
 * loyalty and notification events, which are about customers) with finding them by correlation id.
 */
public final class CausationVsClocks {

    public record Result(int orders, int causalLinks, int linksInvertedByTimestamp, int ordersMisordered,
                         int eventsFoundByOrderId, int eventsFoundByCorrelationId, int events) {
    }

    record Ev(String id, String type, String service, int instance, double trueTime, double recordedTime,
              String causationId, boolean hasOrderId) {
    }

    static final String[][] FLOW = {                       // type, service, cause type, carries orderId
        {"OrderPlaced", "order", "", "y"},
        {"PaymentCaptured", "payment", "OrderPlaced", "y"},
        {"KitchenAccepted", "kitchen", "PaymentCaptured", "y"},
        {"LoyaltyPointsAdded", "loyalty", "PaymentCaptured", "n"},
        {"RiderAssigned", "dispatch", "KitchenAccepted", "y"},
        {"CustomerNotified", "notification", "RiderAssigned", "n"},
    };

    public static Result run(int orders, double driftingInstanceMs, long seed) {
        Random random = new Random(seed);
        Map<String, double[]> offsets = new HashMap<>();
        for (String[] step : FLOW) {
            double[] o = new double[3];
            for (int i = 0; i < 3; i++) {
                o[i] = random.nextGaussian() * 3;           // ms; well-synchronised clocks
            }
            offsets.put(step[1], o);
        }
        offsets.get("kitchen")[2] = driftingInstanceMs;    // one instance with a drifting clock (or 0)

        int links = 0;
        int inverted = 0;
        int misordered = 0;
        int byOrderId = 0;
        int events = 0;
        for (int n = 0; n < orders; n++) {
            Map<String, Ev> byType = new HashMap<>();
            List<Ev> flow = new ArrayList<>();
            double t = random.nextDouble() * 1_000_000;
            for (String[] step : FLOW) {
                Ev cause = step[2].isEmpty() ? null : byType.get(step[2]);
                double trueTime = cause == null ? t : cause.trueTime() + 2 + random.nextDouble() * 58;
                int instance = random.nextInt(3);
                double recorded = trueTime + offsets.get(step[1])[instance];
                Ev e = new Ev(n + "-" + step[0], step[0], step[1], instance, trueTime, recorded,
                        cause == null ? null : cause.id(), step[3].equals("y"));
                byType.put(step[0], e);
                flow.add(e);
            }
            boolean wrong = false;
            for (Ev e : flow) {
                events++;
                if (e.hasOrderId()) {
                    byOrderId++;
                }
                if (e.causationId() == null) {
                    continue;
                }
                Ev cause = flow.stream().filter(c -> c.id().equals(e.causationId())).findFirst().orElseThrow();
                links++;
                if (e.recordedTime() < cause.recordedTime()) {
                    inverted++;
                    wrong = true;
                }
            }
            if (wrong) {
                misordered++;
            }
        }
        // Following causation ids reproduces the true chain by construction: every link is exact,
        // and the correlation id is on every event.
        return new Result(orders, links, inverted, misordered, byOrderId, events, events);
    }

    public static void main(String[] args) {
        System.out.println("10,000 orders x 6 events (50,000 cause -> effect links); clocks within a few ms\n");
        System.out.printf("%-38s %22s %24s %14s%n", "", "links inverted by time", "orders misordered by time",
                "by causation");
        for (double drift : new double[] {0, -250}) {
            Result r = run(10_000, drift, 28);
            System.out.printf("%-38s %14d (%4.1f%%) %16d (%4.1f%%) %14d%n",
                    drift == 0 ? "all clocks within a few ms" : "one kitchen instance 250 ms behind",
                    r.linksInvertedByTimestamp(), 100.0 * r.linksInvertedByTimestamp() / r.causalLinks(),
                    r.ordersMisordered(), 100.0 * r.ordersMisordered() / r.orders(), 0);
        }
        Result r = run(10_000, 0, 28);
        System.out.printf("%nevents of an order found by its orderId field: %,d of %,d (%.0f%%); by correlation id: all %,d%n",
                r.eventsFoundByOrderId(), r.events(), 100.0 * r.eventsFoundByOrderId() / r.events(), r.events());
    }
}
