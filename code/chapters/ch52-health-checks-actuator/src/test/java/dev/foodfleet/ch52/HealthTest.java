package dev.foodfleet.ch52;

import dev.foodfleet.ch52.availability.Availability;
import dev.foodfleet.ch52.health.HealthCheck;
import dev.foodfleet.ch52.health.HealthGroup;
import dev.foodfleet.ch52.health.Status;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class HealthTest {

    @Test
    void aGroupIsAsHealthyAsItsWorstMember() {
        Map<String, HealthCheck> m = new LinkedHashMap<>();
        m.put("a", HealthCheck.Result::up);
        m.put("b", () -> new HealthCheck.Result(Status.OUT_OF_SERVICE, Map.of()));
        assertThat(new HealthGroup("g", m, Duration.ofSeconds(1)).report().status()).isEqualTo(Status.OUT_OF_SERVICE);
        m.put("c", () -> HealthCheck.Result.down("x"));
        assertThat(new HealthGroup("g", m, Duration.ofSeconds(1)).report().status()).isEqualTo(Status.DOWN);
    }

    @Test
    void downAndOutOfServiceFailAnHttpProbe() {
        assertThat(Status.DOWN.httpStatus()).isEqualTo(503);
        assertThat(Status.OUT_OF_SERVICE.httpStatus()).isEqualTo(503);
        assertThat(Status.UP.httpStatus()).isEqualTo(200);
        assertThat(Status.UNKNOWN.httpStatus()).isEqualTo(200);
        assertThat(Status.worst(Status.UP, Status.UNKNOWN)).isEqualTo(Status.UP);
    }

    @Test
    void aHangingCheckIsDownWithinTheGroupTimeout() {
        HealthGroup g = new HealthGroup("g", Map.of("slow", () -> {
            Thread.sleep(5_000);
            return HealthCheck.Result.up();
        }), Duration.ofMillis(200));
        long t = System.nanoTime();
        HealthGroup.Report r = g.report();
        assertThat(r.status()).isEqualTo(Status.DOWN);
        assertThat((System.nanoTime() - t) < 1_000_000_000L).isTrue();
        assertThat(r.json(true)).contains("timed out after 200 ms");
    }

    @Test
    void aThrowingCheckIsDownWithTheReason() {
        HealthGroup g = new HealthGroup("g", Map.of("db", () -> {
            throw new java.net.ConnectException("Connection refused");
        }), Duration.ofSeconds(1));
        assertThat(g.report().json(true)).contains("\"db\":{\"status\":\"DOWN\"").contains("Connection refused");
        assertThat(g.report().json(false)).isEqualTo("{\"status\":\"DOWN\"}");
    }

    @Test
    void anEmptyGroupIsUnknown() {
        assertThat(new HealthGroup("g", Map.of(), Duration.ofSeconds(1)).report().status()).isEqualTo(Status.UNKNOWN);
    }

    @Test
    void availabilityStatesMapLikeSpringBoots() throws Exception {
        Availability a = new Availability();
        assertThat(a.readinessCheck().check().status()).isEqualTo(Status.OUT_OF_SERVICE);   // until startup completes
        a.readiness(Availability.Readiness.ACCEPTING_TRAFFIC);
        assertThat(a.readinessCheck().check().status()).isEqualTo(Status.UP);
        assertThat(a.livenessCheck().check().status()).isEqualTo(Status.UP);
        a.liveness(Availability.Liveness.BROKEN);
        assertThat(a.livenessCheck().check().status()).isEqualTo(Status.DOWN);
    }

    @Test
    void cachedCheckCallsTheDependencyOncePerTtl() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        long[] now = {0};
        HealthCheck db = () -> {
            calls.incrementAndGet();
            return HealthCheck.Result.up();
        };
        var cached = new dev.foodfleet.ch52.health.CachedCheckAccess().create(db, Duration.ofSeconds(10), () -> now[0]);
        cached.check();
        cached.check();
        now[0] = 9_999_999_999L;
        cached.check();
        assertThat(calls.get()).isEqualTo(1);
        now[0] = 10_000_000_000L;
        cached.check();
        assertThat(calls.get()).isEqualTo(2);
    }
}
