package dev.foodfleet.lab10;

import dev.foodfleet.ch14.choreography.ChoreographedPlaceOrder;
import dev.foodfleet.ch14.choreography.EventBus;
import dev.foodfleet.ch14.placeorder.Faults;
import dev.foodfleet.ch14.placeorder.Faults.Fault;
import dev.foodfleet.ch14.placeorder.PlaceOrderSaga;
import dev.foodfleet.ch14.placeorder.PlaceOrderSaga.Request;
import dev.foodfleet.ch14.placeorder.Services;
import dev.foodfleet.ch14.saga.SagaLog;
import dev.foodfleet.ch14.saga.SagaOrchestrator;
import dev.foodfleet.ch14.saga.SagaStatus;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lab 10: runs one place-order saga per scenario, orchestrated and choreographed, with scripted faults,
 * and prints what each style leaves behind: the orchestrator's log, and the events on the bus.
 * Then checks the saga's promises: no money held for a rejected order, no coupon leaked.
 * JDK only. Exits 1 if a promise is broken, so the exercises can be checked by running it again.
 */
public final class SagaTrace {

    /** "payment.authorize" -> REJECT, then NONE for every later call. */
    static final class Script implements Faults {
        private final Map<String, Deque<Fault>> faults = new HashMap<>();

        Script on(String participantDotOperation, Fault... script) {
            faults.computeIfAbsent(participantDotOperation, k -> new ArrayDeque<>()).addAll(List.of(script));
            return this;
        }

        @Override
        public Fault next(String participant, String operation, String sagaId) {
            Deque<Fault> queue = faults.get(participant + "." + operation);
            return queue == null || queue.isEmpty() ? Fault.NONE : queue.poll();
        }
    }

    record Scenario(String name, String participantDotOperation, Fault... faults) {
        Script script() {
            Script s = new Script();
            return participantDotOperation == null ? s : s.on(participantDotOperation, faults);
        }
    }

    static final List<Scenario> SCENARIOS = List.of(
            new Scenario("happy path", null),
            new Scenario("card declined", "payment.authorize", Fault.REJECT),
            new Scenario("kitchen rejects (at the pivot)", "kitchen.accept", Fault.REJECT),
            new Scenario("authorize: reply lost 3 times", "payment.authorize", Fault.LOST_REPLY, Fault.LOST_REPLY, Fault.LOST_REPLY),
            new Scenario("rider service down (after the pivot)", "delivery.request-rider", Fault.TRANSIENT, Fault.TRANSIENT, Fault.TRANSIENT));

    static final Request ORDER = new Request("o-1", 450_00, true);
    static final int MAX_ATTEMPTS = 3;

    public static void main(String[] args) {
        int broken = 0;
        for (Scenario scenario : SCENARIOS) {
            System.out.println("== " + scenario.name() + " ==");
            broken += orchestrated(scenario);
            broken += choreographed(scenario);
            System.out.println();
        }
        System.out.println(broken == 0 ? "all promises kept" : broken + " promise(s) broken");
        System.exit(broken == 0 ? 0 : 1);
    }

    static int orchestrated(Scenario scenario) {
        Script faults = scenario.script();
        Services.Orders orders = new Services.Orders(faults);
        Services.Coupons coupons = new Services.Coupons(faults, 10);
        Services.Payments payments = new Services.Payments(faults, true);
        Services.Kitchen kitchen = new Services.Kitchen(faults);
        Services.Delivery delivery = new Services.Delivery(faults);
        SagaLog log = new SagaLog();
        var orchestrator = new SagaOrchestrator<>(PlaceOrderSaga.definition(orders, coupons, payments, kitchen, delivery),
                log, MAX_ATTEMPTS, SagaOrchestrator.CrashPolicy.NEVER);

        SagaStatus status = orchestrator.start("s-1", ORDER);
        System.out.println("orchestrated -> " + status);
        System.out.println("  log: " + String.join(" > ", log.history("s-1").stream().map(SagaLog.Entry::what).toList()));
        if (status == SagaStatus.STUCK) {
            // The rider service is back: an operator (or a scheduled job) retries from the log.
            status = orchestrator.retryStuck("s-1", ORDER);
            System.out.println("  after retryStuck -> " + status);
        }
        return check("orchestrated", status, orders, coupons, payments);
    }

    static int choreographed(Scenario scenario) {
        Script faults = scenario.script();
        Services.Orders orders = new Services.Orders(faults);
        Services.Coupons coupons = new Services.Coupons(faults, 10);
        Services.Payments payments = new Services.Payments(faults, true);
        Services.Delivery delivery = new Services.Delivery(faults);
        EventBus bus = new EventBus(MAX_ATTEMPTS);
        new ChoreographedPlaceOrder(bus, orders, coupons, payments, new Services.Kitchen(faults), delivery)
                .placeOrder("s-1", ORDER, orders);
        System.out.println("choreographed: no saga log; the events were:");
        System.out.println("  events: " + String.join(" > ", bus.trace()));
        // No component knows the saga's status: we read each service's own state to find out.
        System.out.printf("  choreographed: order %s, held ₹%,d, coupon uses left %d, captures %d, riders %d%n",
                orders.status.get("o-1"), payments.heldPaise.get() / 100, coupons.remainingUses.get(),
                payments.captures.get(), delivery.requests.get());
        return 0;   // reported, not checked: the dead letters are a human's job here
    }

    /** The promises: an approved order is paid for; a rejected one holds no money and no coupon. */
    static int check(String style, SagaStatus status, Services.Orders orders, Services.Coupons coupons, Services.Payments payments) {
        Services.Orders.Status order = orders.status.get("o-1");
        boolean kept = switch (status) {
            case COMPLETED -> order == Services.Orders.Status.APPROVED && payments.captures.get() == 1
                    && coupons.remainingUses.get() == 9;
            case COMPENSATED -> order == Services.Orders.Status.REJECTED && payments.heldPaise.get() == 0
                    && coupons.remainingUses.get() == 10;
            default -> false;
        };
        System.out.printf("  %s: order %s, held ₹%,d, coupon uses left %d, captures %d -> %s%n", style, order,
                payments.heldPaise.get() / 100, coupons.remainingUses.get(), payments.captures.get(), kept ? "ok" : "BROKEN");
        return kept ? 0 : 1;
    }
}
