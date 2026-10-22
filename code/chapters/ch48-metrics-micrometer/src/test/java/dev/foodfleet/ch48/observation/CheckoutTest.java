package dev.foodfleet.ch48.observation;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.observation.DefaultMeterObservationHandler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CheckoutTest {

    /** Stands in for the tracing handler: records every key value it is given. */
    static final class Recorder implements ObservationHandler<Observation.Context> {
        final List<String> keys = new ArrayList<>();

        @Override
        public void onStop(Observation.Context context) {
            context.getAllKeyValues().forEach(kv -> keys.add(kv.getKey()));
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return true;
        }
    }

    @Test
    void lowCardinalityKeysBecomeTagsHighCardinalityKeysDoNot() {
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        Recorder spans = new Recorder();
        ObservationRegistry observations = ObservationRegistry.create();
        observations.observationConfig().observationHandler(new DefaultMeterObservationHandler(meters)).observationHandler(spans);

        new Checkout(observations).place("o-1", "r-42", "upi", "metro", () -> Checkout.Result.PLACED);

        Timer t = meters.get("foodfleet.checkout").timer();
        assertThat(t.count()).isEqualTo(1);
        assertThat(t.getId().getTag("payment.method")).isEqualTo("upi");
        assertThat(t.getId().getTag("city.tier")).isEqualTo("metro");
        assertThat(t.getId().getTag("outcome")).isEqualTo("placed");
        assertThat(t.getId().getTag("order.id")).isNull();
        assertThat(t.getId().getTag("restaurant.id")).isNull();
        assertThat(spans.keys).contains("order.id", "restaurant.id", "payment.method", "outcome");
    }

    @Test
    void theMeterHandlerAddsALongTaskTimerUnlessToldNotTo() {
        SimpleMeterRegistry with = new SimpleMeterRegistry();
        ObservationRegistry o1 = ObservationRegistry.create();
        o1.observationConfig().observationHandler(new DefaultMeterObservationHandler(with));
        new Checkout(o1).place("o-1", "r-1", "card", "tier-2", () -> Checkout.Result.PAYMENT_DECLINED);

        SimpleMeterRegistry without = new SimpleMeterRegistry();
        ObservationRegistry o2 = ObservationRegistry.create();
        o2.observationConfig().observationHandler(new DefaultMeterObservationHandler(without,
                DefaultMeterObservationHandler.IgnoredMeters.LONG_TASK_TIMER));
        new Checkout(o2).place("o-1", "r-1", "card", "tier-2", () -> Checkout.Result.PAYMENT_DECLINED);

        assertThat(with.find("foodfleet.checkout.active").longTaskTimers()).hasSize(1);
        assertThat(without.find("foodfleet.checkout.active").longTaskTimers()).isEmpty();
        assertThat(without.get("foodfleet.checkout").tag("outcome", "payment_declined").timer().count()).isEqualTo(1);
    }

    @Test
    void anObservationWithNoRegistryCostsNothingAndRecordsNothing() {
        Checkout.Result r = new Checkout(ObservationRegistry.NOOP).place("o-1", "r-1", "upi", "metro",
                () -> Checkout.Result.RESTAURANT_CLOSED);
        assertThat(r).isEqualTo(Checkout.Result.RESTAURANT_CLOSED);
    }
}
