package dev.foodfleet.ch23.deadline;

import static org.assertj.core.api.Assertions.assertThat;

import dev.foodfleet.ch23.deadline.DeadlineSimulation.Mode;
import org.junit.jupiter.api.Test;

class DeadlineSimulationTest {

    @Test
    void whenEverythingIsHealthyDeadlinesChangeNothing() {
        var fixed = DeadlineSimulation.run(Mode.FIXED_HOP_TIMEOUTS, 2_000, 0.0, 7);
        var propagated = DeadlineSimulation.run(Mode.PROPAGATED_DEADLINE, 2_000, 0.0, 7);

        assertThat(fixed.succeededPercent()).isEqualTo(100.0);
        assertThat(propagated.succeededPercent()).isEqualTo(100.0);
        assertThat(fixed.wastedMsPerRequest()).isEqualTo(0.0);
    }

    @Test
    void aPropagatedDeadlineStopsWorkNobodyWillRead() {
        var fixed = DeadlineSimulation.run(Mode.FIXED_HOP_TIMEOUTS, 5_000, 0.6, 7);
        var propagated = DeadlineSimulation.run(Mode.PROPAGATED_DEADLINE, 5_000, 0.6, 7);

        assertThat(propagated.succeededPercent()).isEqualTo(fixed.succeededPercent());   // users see the same
        assertThat(propagated.mapsBusyMsPerRequest()).isLessThan(fixed.mapsBusyMsPerRequest() / 2);
        assertThat(propagated.wastedMsPerRequest()).isLessThan(10.0);
        assertThat(fixed.wastedMsPerRequest()).isGreaterThan(300.0);
        assertThat(propagated.p99HeldMs()).isLessThan(510.0);
    }
}
