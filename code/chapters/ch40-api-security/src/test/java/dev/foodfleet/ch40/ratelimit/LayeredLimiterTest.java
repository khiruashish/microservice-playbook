package dev.foodfleet.ch40.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import dev.foodfleet.ch40.ratelimit.LayeredLimiter.Caller;
import dev.foodfleet.ch40.ratelimit.LayeredLimiter.Layer;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class LayeredLimiterTest {

    static LayeredLimiter limits() {
        return new LayeredLimiter(List.of(
                new Layer(new GcraLimiter("client", 10, Duration.ofSeconds(1), 10), c -> c.tenant() + "/" + c.client()),
                new Layer(new GcraLimiter("tenant", 15, Duration.ofSeconds(1), 15), Caller::tenant)));
    }

    @Test
    void oneClientIsStoppedByItsOwnLimit() {
        LayeredLimiter l = limits();
        Caller pos = new Caller("spice-route", "pos-sync", "10.1.1.1");
        int allowed = 0;
        for (int i = 0; i < 50; i++) {
            allowed += l.tryAcquire(pos, 0).decision().allowed() ? 1 : 0;
        }
        assertThat(allowed).isEqualTo(10);
        LayeredLimiter.Outcome denied = l.tryAcquire(pos, 0);
        assertThat(denied.policy().name()).isEqualTo("client");
    }

    @Test
    void aTenantsClientsShareItsLimitAndOtherTenantsAreUntouched() {
        LayeredLimiter l = limits();
        int tenantA = 0;
        for (int i = 0; i < 10; i++) {
            tenantA += l.tryAcquire(new Caller("spice-route", "pos-1", "a"), 0).decision().allowed() ? 1 : 0;
            tenantA += l.tryAcquire(new Caller("spice-route", "pos-2", "a"), 0).decision().allowed() ? 1 : 0;
        }
        assertThat(tenantA).isEqualTo(15);
        LayeredLimiter.Outcome other = l.tryAcquire(new Caller("dosa-co", "pos-1", "b"), 0);
        assertThat(other.decision().allowed()).isTrue();
        assertThat(l.tryAcquire(new Caller("spice-route", "pos-3", "a"), 0).policy().name()).isEqualTo("tenant");
    }

    @Test
    void anAllowanceReportsTheTightestLayer() {
        LayeredLimiter l = limits();
        LayeredLimiter.Outcome first = l.tryAcquire(new Caller("t", "c", "a"), 0);
        assertThat(first.policy().name()).isEqualTo("client");
        assertThat(first.decision().remaining()).isEqualTo(9);
    }
}
