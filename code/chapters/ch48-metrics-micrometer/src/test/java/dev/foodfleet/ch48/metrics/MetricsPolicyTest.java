package dev.foodfleet.ch48.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.CountAtBucket;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class MetricsPolicyTest {

    private static SimpleMeterRegistry registry() {
        SimpleMeterRegistry r = new SimpleMeterRegistry();
        MetricsPolicy.foodFleet("ap-south-1").forEach(f -> r.config().meterFilter(f));
        return r;
    }

    @Test
    void everyMeterGetsTheRegionAndNoIdentifiers() {
        SimpleMeterRegistry r = registry();
        Counter c = Counter.builder("foodfleet.orders").tag("payment.method", "upi").tag("order.id", "o-123").register(r);

        assertThat(c.getId().getTag("region")).isEqualTo("ap-south-1");
        assertThat(c.getId().getTag("order.id")).isNull();
        assertThat(c.getId().getTag("payment.method")).isEqualTo("upi");
    }

    @Test
    void uriValuesAreCappedAndLaterOnesAreDropped() {
        SimpleMeterRegistry r = registry();
        for (int i = 0; i < 300; i++) {
            Timer.builder("http.server.requests").tag("uri", "/orders/" + i).register(r).record(Duration.ofMillis(5));
        }
        long timers = r.find("http.server.requests").timers().size();
        assertThat(timers).isEqualTo(200);
        // the 201st value gets a no-op timer: what it records goes nowhere
        Timer late = Timer.builder("http.server.requests").tag("uri", "/orders/{id}/cancel").register(r);
        late.record(Duration.ofMillis(5));
        assertThat(r.find("http.server.requests").tag("uri", "/orders/{id}/cancel").timers()).isEmpty();
    }

    @Test
    void httpTimersGetHistogramsWithTheSloBoundaries() {
        SimpleMeterRegistry r = registry();
        Timer t = Timer.builder("http.server.requests").tag("uri", "/orders").register(r);
        t.record(Duration.ofMillis(120));
        t.record(Duration.ofMillis(300));

        boolean has250 = false;
        for (CountAtBucket b : t.takeSnapshot().histogramCounts()) {
            if (b.bucket() == Duration.ofMillis(250).toNanos()) {
                has250 = true;
                assertThat(b.count()).isEqualTo(1.0);
            }
        }
        assertThat(has250).isTrue();
    }

    @Test
    void boundingTheRangeTrimsBucketsAndAddsTheSloOnes() {
        PrometheusMeterRegistry plain = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        Timer.builder("orders").publishPercentileHistogram().register(plain).record(Duration.ofMillis(5));
        PrometheusMeterRegistry bounded = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        bounded.config().meterFilter(MetricsPolicy.boundedLatencyHistograms("orders"));
        Timer.builder("orders").register(bounded).record(Duration.ofMillis(5));

        long plainBuckets = plain.scrape().lines().filter(l -> l.startsWith("orders_seconds_bucket")).count();
        long boundedBuckets = bounded.scrape().lines().filter(l -> l.startsWith("orders_seconds_bucket")).count();
        assertThat(plainBuckets).isEqualTo(69);
        assertThat(boundedBuckets).isEqualTo(65);
        assertThat(bounded.scrape()).contains("le=\"0.25\"");
        assertThat(plain.scrape().contains("le=\"0.25\"")).isFalse();
    }
}
