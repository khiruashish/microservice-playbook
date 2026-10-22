package dev.foodfleet.ch48.metrics;

import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static dev.foodfleet.ch48.metrics.OrderMetrics.Outcome.PAYMENT_DECLINED;
import static dev.foodfleet.ch48.metrics.OrderMetrics.Outcome.PLACED;
import static dev.foodfleet.ch48.metrics.OrderMetrics.PaymentMethod.CARD;
import static dev.foodfleet.ch48.metrics.OrderMetrics.PaymentMethod.UPI;
import static org.assertj.core.api.Assertions.assertThat;

class OrderMetricsTest {

    @Test
    void theNumberOfSeriesIsFixedByTheEnums() {
        SimpleMeterRegistry r = new SimpleMeterRegistry();
        new OrderMetrics().bindTo(r);

        assertThat(r.find("foodfleet.orders").counters()).hasSize(9);
        // 9 counters, the value summary, the kitchen gauge, the delivery timer, and (in the simple registry) one gauge
        // per SLO boundary of the value summary
        assertThat(r.getMeters()).hasSize(16);
        assertThat(r.find("foodfleet.order.value.histogram").gauges()).hasSize(4);
    }

    @Test
    void recordsOrdersValuesAndTheKitchenQueue() {
        SimpleMeterRegistry r = new SimpleMeterRegistry();
        OrderMetrics m = new OrderMetrics();
        m.bindTo(r);

        m.placed(UPI, PLACED, 450);
        m.placed(UPI, PLACED, 1200);
        m.placed(CARD, PAYMENT_DECLINED, 300);
        m.pickedUp();

        assertThat(r.get("foodfleet.orders").tags("payment.method", "upi", "outcome", "placed").counter().count()).isEqualTo(2.0);
        assertThat(r.get("foodfleet.orders").tags("payment.method", "card", "outcome", "payment_declined").counter().count())
                .isEqualTo(1.0);
        assertThat(r.get("foodfleet.order.value").summary().totalAmount()).isEqualTo(1650.0);
        assertThat(r.get("foodfleet.kitchen.queue").gauge().value()).isEqualTo(1.0);
    }

    @Test
    void theKitchenGaugeSurvivesGarbageCollection() {
        SimpleMeterRegistry r = new SimpleMeterRegistry();
        new OrderMetrics().bindTo(r);           // the binder itself is not kept
        for (int i = 0; i < 3; i++) {
            System.gc();
        }
        assertThat(Double.isNaN(r.get("foodfleet.kitchen.queue").gauge().value())).isFalse();
    }

    @Test
    void activeDeliveriesAreVisibleWhileTheyRun() {
        SimpleMeterRegistry r = new SimpleMeterRegistry();
        OrderMetrics m = new OrderMetrics();
        m.bindTo(r);

        LongTaskTimer.Sample d = m.deliveryStarted();
        assertThat(r.get("foodfleet.deliveries.active").longTaskTimer().activeTasks()).isEqualTo(1);
        d.stop();
        assertThat(r.get("foodfleet.deliveries.active").longTaskTimer().activeTasks()).isEqualTo(0);
    }
}
