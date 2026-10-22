package dev.foodfleet.ch14.simulation;

import dev.foodfleet.ch14.placeorder.PlaceOrderSaga;
import dev.foodfleet.ch14.placeorder.PlaceOrderSaga.Request;
import dev.foodfleet.ch14.placeorder.Services;
import dev.foodfleet.ch14.saga.SagaDefinition;
import dev.foodfleet.ch14.saga.SagaLog;
import dev.foodfleet.ch14.saga.SagaOrchestrator;
import dev.foodfleet.ch14.saga.SagaStatus;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Runs many place-order sagas against participants with injected failures and orchestrator
 * crashes, then retries the stuck ones once the participants are healthy, and checks that money
 * and coupons add up.
 */
public final class SagaSimulation {

    public static final long ORDER_PAISE = 500_00;

    /** FoodFleet-like failure rates per participant operation (illustrative). */
    public static Map<String, RandomFaults.Odds> foodFleetOdds() {
        Map<String, RandomFaults.Odds> odds = new HashMap<>();
        odds.put("promotions.reserve", new RandomFaults.Odds(0.02, 0.01, 0.0));
        odds.put("promotions.release", new RandomFaults.Odds(0, 0.01, 0.0));
        odds.put("payment.authorize", new RandomFaults.Odds(0.08, 0.02, 0.01));
        odds.put("payment.void", new RandomFaults.Odds(0, 0.02, 0.005));
        odds.put("kitchen.accept", new RandomFaults.Odds(0.05, 0.10, 0.005));     // tablets at restaurants time out often
        odds.put("payment.capture", new RandomFaults.Odds(0, 0.02, 0.005));
        odds.put("delivery.request-rider", new RandomFaults.Odds(0, 0.15, 0.0));   // no rider free: "try again"
        odds.put("ordering.approve", new RandomFaults.Odds(0, 0.005, 0.0));
        return odds;
    }

    public record Report(int sagas, Map<SagaStatus, Integer> firstPass, Map<SagaStatus, Integer> afterRetry,
                         int crashes, long authorizationsHeldPaise, long expectedHeldPaise, int voids,
                         int couponsLeaked, int approvedWithoutKitchen) {

        public long extraHeldPaise() {
            return authorizationsHeldPaise - expectedHeldPaise;
        }
    }

    public Report run(int sagas, boolean idempotentPayments, double crashProbability, long seed) {
        RandomFaults faults = new RandomFaults(foodFleetOdds(), seed);
        SplittableRandom random = new SplittableRandom(seed + 1);
        int couponUses = 1_000_000;
        Services.Orders orders = new Services.Orders(faults);
        Services.Coupons coupons = new Services.Coupons(faults, couponUses);
        Services.Payments payments = new Services.Payments(faults, idempotentPayments);
        Services.Kitchen kitchen = new Services.Kitchen(faults);
        Services.Delivery delivery = new Services.Delivery(faults);
        SagaDefinition<Request> definition = PlaceOrderSaga.definition(orders, coupons, payments, kitchen, delivery);
        SagaLog log = new SagaLog();
        int[] crashes = {0};
        SagaOrchestrator.CrashPolicy crashPolicy = (id, step) -> {
            boolean crash = random.nextDouble() < crashProbability;
            if (crash) {
                crashes[0]++;
            }
            return crash;
        };

        Map<String, Request> requests = new HashMap<>();
        Map<SagaStatus, Integer> firstPass = new EnumMap<>(SagaStatus.class);
        for (int n = 0; n < sagas; n++) {
            String sagaId = "saga-" + n;
            Request request = new Request("o-" + n, ORDER_PAISE, random.nextDouble() < 0.4);
            requests.put(sagaId, request);
            SagaStatus status = null;
            while (status == null) {
                try {
                    // a fresh orchestrator each time: after a crash, a new process resumes from the log
                    status = new SagaOrchestrator<>(definition, log, 3, crashPolicy).start(sagaId, request);
                } catch (SagaOrchestrator.Crash crash) {
                    // restart and resume
                }
            }
            firstPass.merge(status, 1, Integer::sum);
        }

        faults.heal();
        SagaOrchestrator<Request> recovery = new SagaOrchestrator<>(definition, log, 3, SagaOrchestrator.CrashPolicy.NEVER);
        Map<SagaStatus, Integer> afterRetry = new EnumMap<>(SagaStatus.class);
        for (Map.Entry<String, Request> e : requests.entrySet()) {
            afterRetry.merge(recovery.retryStuck(e.getKey(), e.getValue()), 1, Integer::sum);
        }

        long approved = orders.status.values().stream().filter(s -> s == Services.Orders.Status.APPROVED).count();
        long couponsHeld = requests.entrySet().stream()
                .filter(e -> e.getValue().withCoupon()
                        && orders.status.get(e.getValue().orderId()) == Services.Orders.Status.APPROVED)
                .count();
        int leaked = (int) (couponUses - coupons.remainingUses.get() - couponsHeld);
        int approvedWithoutKitchen = (int) Math.max(0, approved - kitchen.tickets.get());
        return new Report(sagas, firstPass, afterRetry, crashes[0], payments.heldPaise.get(), approved * ORDER_PAISE,
                payments.voids.get(), leaked, approvedWithoutKitchen);
    }

    public static String render(Report r, String title) {
        StringBuilder out = new StringBuilder("== " + title + " ==\n");
        out.append(String.format(Locale.ROOT, "%d sagas, %d orchestrator crashes (resumed from the log)%n", r.sagas(), r.crashes()));
        out.append("first pass:  ").append(r.firstPass()).append('\n');
        out.append("after retry: ").append(r.afterRetry()).append('\n');
        out.append(String.format(Locale.ROOT, "authorizations voided: %d%n", r.voids()));
        out.append(String.format(Locale.ROOT, "money held: ₹%,d, expected ₹%,d, extra ₹%,d (%d double charges)%n",
                r.authorizationsHeldPaise() / 100, r.expectedHeldPaise() / 100, r.extraHeldPaise() / 100,
                r.extraHeldPaise() / ORDER_PAISE));
        out.append(String.format(Locale.ROOT, "coupons leaked: %d, approved orders without a kitchen ticket: %d%n",
                r.couponsLeaked(), r.approvedWithoutKitchen()));
        return out.toString();
    }

    public static void main(String[] args) {
        SagaSimulation simulation = new SagaSimulation();
        for (var run : List.of(
                Map.entry("idempotent participants", true),
                Map.entry("payment-service NOT idempotent", false))) {
            System.out.println(render(simulation.run(10_000, run.getValue(), 0.005, 7), run.getKey()));
        }
    }
}
