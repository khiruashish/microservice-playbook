package dev.foodfleet.ch48.observation;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

import java.util.function.Supplier;

import static dev.foodfleet.ch48.observation.CheckoutObservation.HighCardinality.ORDER_ID;
import static dev.foodfleet.ch48.observation.CheckoutObservation.HighCardinality.RESTAURANT_ID;
import static dev.foodfleet.ch48.observation.CheckoutObservation.LowCardinality.CITY_TIER;
import static dev.foodfleet.ch48.observation.CheckoutObservation.LowCardinality.OUTCOME;
import static dev.foodfleet.ch48.observation.CheckoutObservation.LowCardinality.PAYMENT_METHOD;

/** Places an order inside an Observation: one instrumentation point for metrics, traces and anything else. */
public final class Checkout {

    /** What placing an order returned. */
    public enum Result { PLACED, PAYMENT_DECLINED, RESTAURANT_CLOSED }

    private final ObservationRegistry registry;

    public Checkout(ObservationRegistry registry) {
        this.registry = registry;
    }

    public Result place(String orderId, String restaurantId, String paymentMethod, String cityTier, Supplier<Result> work) {
        Observation observation = CheckoutObservation.PLACE_ORDER.observation(registry)
                .lowCardinalityKeyValue(PAYMENT_METHOD.withValue(paymentMethod))
                .lowCardinalityKeyValue(CITY_TIER.withValue(cityTier))
                .highCardinalityKeyValue(ORDER_ID.withValue(orderId))
                .highCardinalityKeyValue(RESTAURANT_ID.withValue(restaurantId));
        return observation.observe(() -> {
            Result r = work.get();
            // the outcome is known only at the end, and it is still low cardinality: an enum
            observation.lowCardinalityKeyValue(OUTCOME.withValue(r.name().toLowerCase()));
            return r;
        });
    }
}
