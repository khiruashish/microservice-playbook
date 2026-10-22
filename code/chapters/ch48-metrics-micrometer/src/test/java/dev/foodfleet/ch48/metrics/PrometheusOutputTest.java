package dev.foodfleet.ch48.metrics;

import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** What Micrometer's Prometheus registry actually writes, and how long its windowed statistics remember a spike. */
class PrometheusOutputTest {

    @Test
    void clientSidePercentilesAreASummary() {
        PrometheusMeterRegistry r = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        Timer.builder("orders").publishPercentiles(0.99).register(r).record(Duration.ofMillis(20));

        assertThat(r.scrape()).contains("# TYPE orders_seconds summary", "orders_seconds{quantile=\"0.99\"}");
    }

    @Test
    void withAHistogramTheClientSidePercentilesAreLeftOut() {
        PrometheusMeterRegistry r = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        Timer.builder("orders").publishPercentiles(0.99).publishPercentileHistogram().register(r).record(Duration.ofMillis(20));

        String scrape = r.scrape();
        assertThat(scrape).contains("# TYPE orders_seconds histogram", "orders_seconds_bucket{le=");
        assertThat(scrape.contains("quantile=")).isFalse();
    }

    @Test
    void percentilesForgetASpikeAfterAMinuteButMaxRemembersLonger() {
        MockClock clock = new MockClock();
        PrometheusMeterRegistry r = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT, new PrometheusRegistry(), clock);
        Timer t = Timer.builder("orders").publishPercentiles(0.99).register(r);
        t.record(Duration.ofSeconds(3));                              // the spike

        for (int s = 0; s < 70; s++) {                                // then 70 s of 20 ms orders
            clock.add(Duration.ofSeconds(1));
            t.record(Duration.ofMillis(20));
        }
        double p99 = 0;
        for (ValueAtPercentile v : t.takeSnapshot().percentileValues()) {
            p99 = v.value(TimeUnit.MILLISECONDS);
        }
        assertThat(p99).isLessThan(100.0);                            // rotated out: expiry 1 m (the step) / 3 buffers
        assertThat(t.max(TimeUnit.MILLISECONDS)).isEqualTo(3000.0);   // max rotates once per expiry, through 3 buffers

        clock.add(Duration.ofMinutes(3));
        t.record(Duration.ofMillis(20));
        assertThat(t.max(TimeUnit.MILLISECONDS)).isLessThan(100.0);
    }
}
