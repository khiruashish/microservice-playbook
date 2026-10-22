package dev.foodfleet.ch44.limit;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.concurrency.limits.Limiter;
import com.netflix.concurrency.limits.limit.FixedLimit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AdmissionTest {

    private static Limiter<Priority> partitioned(int limit) {
        return PartitionedLimiter.<Priority>newBuilder()
                .limit(FixedLimit.of(limit))
                .partitionResolver(Priority::partition)
                .partition(Priority.CRITICAL.partition(), 0.9)
                .partition(Priority.SHEDDABLE.partition(), 0.1)
                .build();
    }

    private static List<Limiter.Listener> take(Limiter<Priority> limiter, Priority p, int n) {
        List<Limiter.Listener> taken = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Optional<Limiter.Listener> l = limiter.acquire(p);
            l.ifPresent(taken::add);
        }
        return taken;
    }

    @Test
    void belowTheLimitAnyClassMayUseSpareCapacity() {
        Limiter<Priority> limiter = partitioned(10);
        assertThat(take(limiter, Priority.SHEDDABLE, 10)).hasSize(10);
    }

    @Test
    void atTheLimitCriticalRequestsKeepTheirShare() {
        Limiter<Priority> limiter = partitioned(10);
        List<Limiter.Listener> browse = take(limiter, Priority.SHEDDABLE, 10);     // sheddable took everything
        assertThat(take(limiter, Priority.SHEDDABLE, 1)).isEmpty();
        assertThat(take(limiter, Priority.CRITICAL, 9)).hasSize(9);               // critical still gets its 90%
        assertThat(take(limiter, Priority.CRITICAL, 1)).isEmpty();
        browse.forEach(Limiter.Listener::onSuccess);
    }

    @Test
    void unmarkedRequestsAreSheddable() {
        assertThat(Priority.of(null)).isEqualTo(Priority.SHEDDABLE);
        assertThat(Priority.of("CRITICAL ")).isEqualTo(Priority.CRITICAL);
        assertThat(Priority.of("urgent-please")).isEqualTo(Priority.SHEDDABLE);
    }

    @Test
    void aimdShrinksWhenCallsAreSlowerThanItsThreshold() {
        var limit = AdaptiveLimits.aimd(java.time.Duration.ofMillis(250));
        int before = limit.getLimit();
        limit.onSample(0, 400_000_000L, before, false);    // a 400 ms call
        assertThat(limit.getLimit()).isLessThan(before);
    }
}
