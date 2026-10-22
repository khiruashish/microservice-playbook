package dev.foodfleet.ch48.observation;

import io.micrometer.common.docs.KeyName;
import io.micrometer.observation.docs.ObservationDocumentation;

/**
 * The checkout as an Observation: instrumented once, it becomes a timer (and a long-task timer while running) through
 * the meter handler, a span through the tracing handler, and whatever else is registered. Low-cardinality key values
 * become metric tags and span attributes; high-cardinality ones become span attributes only. Declaring the keys here
 * keeps the split in one place, where a reviewer can see it.
 */
public enum CheckoutObservation implements ObservationDocumentation {

    PLACE_ORDER {
        @Override
        public String getName() {
            return "foodfleet.checkout";
        }

        @Override
        public String getContextualName() {
            return "place order";
        }

        @Override
        public KeyName[] getLowCardinalityKeyNames() {
            return LowCardinality.values();
        }

        @Override
        public KeyName[] getHighCardinalityKeyNames() {
            return HighCardinality.values();
        }
    };

    /** Few values each: safe as metric tags. */
    public enum LowCardinality implements KeyName {
        PAYMENT_METHOD { public String asString() { return "payment.method"; } },
        CITY_TIER { public String asString() { return "city.tier"; } },
        OUTCOME { public String asString() { return "outcome"; } }
    }

    /** One value per order, restaurant or user: spans and events only. */
    public enum HighCardinality implements KeyName {
        ORDER_ID { public String asString() { return "order.id"; } },
        RESTAURANT_ID { public String asString() { return "restaurant.id"; } }
    }
}
