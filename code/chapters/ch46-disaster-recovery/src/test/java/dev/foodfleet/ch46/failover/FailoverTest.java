package dev.foodfleet.ch46.failover;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class FailoverTest {

    private final AtomicLong now = new AtomicLong();
    private final List<String> steps = new ArrayList<>();

    private FailoverController controller(List<Boolean> health, int threshold) {
        Deque<Boolean> script = new ArrayDeque<>(health);
        return new FailoverController(() -> script.isEmpty() ? false : script.poll(),
                () -> steps.add("fence"), () -> steps.add("promote"),
                Duration.ofSeconds(2), threshold, d -> now.addAndGet(d.toNanos()), now::get);
    }

    @Test
    void promotesAfterThresholdConsecutiveFailuresAndFencesFirst() throws Exception {
        FailoverController.Timeline t = controller(List.of(true, true, false, false, false), 3).watch();
        assertThat(steps).containsExactly("fence", "promote");
        assertThat(t.detection()).isEqualTo(Duration.ofSeconds(4));   // failures at 6 s, 8 s, 10 s
    }

    @Test
    void aSingleFailedCheckIsNotAnOutage() throws Exception {
        FailoverController.Timeline t = controller(List.of(false, true, false, true, false, false, false), 3).watch();
        assertThat(Duration.ofNanos(t.firstFailure())).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void theLocatorFindsTheNewPrimaryAndSkipsDeadHosts() {
        PrimaryLocator locator = new PrimaryLocator(List.of("pg-a", "pg-b", "pg-c"), host -> {
            if (host.equals("pg-a")) {
                throw new java.io.IOException("connection refused");
            }
            return host.equals("pg-c");
        });
        assertThat(locator.current()).isEqualTo("pg-a");
        assertThat(locator.relocate()).contains("pg-c");
        assertThat(locator.current()).isEqualTo("pg-c");
    }

    @Test
    void noPrimaryAnywhereIsReportedNotGuessed() {
        PrimaryLocator locator = new PrimaryLocator(List.of("pg-a", "pg-b"), host -> false);
        assertThat(locator.relocate()).isEmpty();
        assertThat(locator.current()).isEqualTo("pg-a");
    }
}
