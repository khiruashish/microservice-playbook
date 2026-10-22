package dev.foodfleet.ch48.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * order-service's business metrics. Every meter is registered once, when the binder is bound, and kept in a field:
 * looking a meter up by name and tags on every order costs a map lookup and allocations each time, and a builder
 * call on a hot path is the usual way an unbounded tag value sneaks in. Tag values come from enums, so the number of
 * series is fixed by the code: 3 payment methods × 3 outcomes = 9 counters.
 */
public final class OrderMetrics implements MeterBinder {

    public enum PaymentMethod { UPI, CARD, WALLET }

    public enum Outcome { PLACED, PAYMENT_DECLINED, RESTAURANT_CLOSED }

    private final Map<PaymentMethod, Map<Outcome, Counter>> orders = new EnumMap<>(PaymentMethod.class);
    private final AtomicInteger kitchenQueue = new AtomicInteger();
    private DistributionSummary orderValue;
    private LongTaskTimer activeDeliveries;

    @Override
    public void bindTo(MeterRegistry registry) {
        for (PaymentMethod m : PaymentMethod.values()) {
            Map<Outcome, Counter> byOutcome = new EnumMap<>(Outcome.class);
            for (Outcome o : Outcome.values()) {
                byOutcome.put(o, Counter.builder("foodfleet.orders")
                        .description("Orders by payment method and outcome")
                        .tag("payment.method", m.name().toLowerCase())
                        .tag("outcome", o.name().toLowerCase())
                        .register(registry));
            }
            orders.put(m, byOutcome);
        }
        orderValue = DistributionSummary.builder("foodfleet.order.value")
                .description("Basket value of placed orders")
                .baseUnit("inr")
                .serviceLevelObjectives(200, 500, 1000, 2000)
                .register(registry);
        // a gauge observes the AtomicInteger through a weak reference by default; this class keeps it alive, and
        // strongReference(true) makes sure of it even if the binder itself is dropped
        Gauge.builder("foodfleet.kitchen.queue", kitchenQueue, AtomicInteger::get)
                .description("Orders accepted and not yet picked up")
                .strongReference(true)
                .register(registry);
        activeDeliveries = LongTaskTimer.builder("foodfleet.deliveries.active")
                .description("Deliveries in progress and how long they have been running")
                .register(registry);
    }

    public void placed(PaymentMethod method, Outcome outcome, double valueInr) {
        orders.get(method).get(outcome).increment();
        if (outcome == Outcome.PLACED) {
            orderValue.record(valueInr);
            kitchenQueue.incrementAndGet();
        }
    }

    public void pickedUp() {
        kitchenQueue.decrementAndGet();
    }

    /** Starts timing a delivery; call {@code stop()} on the sample when it is delivered. */
    public LongTaskTimer.Sample deliveryStarted() {
        return activeDeliveries.start();
    }
}
