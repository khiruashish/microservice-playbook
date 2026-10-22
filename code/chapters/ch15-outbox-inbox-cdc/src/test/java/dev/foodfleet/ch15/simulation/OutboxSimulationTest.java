package dev.foodfleet.ch15.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import dev.foodfleet.ch15.simulation.OutboxSimulation.Design;
import dev.foodfleet.ch15.simulation.OutboxSimulation.Report;
import org.junit.jupiter.api.Test;

class OutboxSimulationTest {

    @Test
    void dualWritesLoseOrInventEvents() {
        Report databaseFirst = OutboxSimulation.run(Design.DUAL_WRITE_DATABASE_FIRST, 3_000, 3);
        Report brokerFirst = OutboxSimulation.run(Design.DUAL_WRITE_BROKER_FIRST, 3_000, 3);

        assertThat(databaseFirst.lostEvents()).isPositive();
        assertThat(databaseFirst.phantomEvents()).isZero();
        assertThat(brokerFirst.phantomEvents()).isPositive();
        assertThat(brokerFirst.lostEvents()).isZero();
    }

    @Test
    void theOutboxNeverLosesOrInventsButDuplicates() {
        Report report = OutboxSimulation.run(Design.OUTBOX_POLLING_NO_INBOX, 3_000, 3);

        assertThat(report.lostEvents()).isZero();
        assertThat(report.phantomEvents()).isZero();
        assertThat(report.duplicateMessages()).isPositive();
        assertThat(report.pointsWrong()).isPositive();          // duplicates applied twice
    }

    @Test
    void outboxPlusInboxIsConsistentWithPollingAndWithCdc() {
        for (Design design : new Design[] {Design.OUTBOX_POLLING_INBOX, Design.OUTBOX_CDC_INBOX}) {
            Report report = OutboxSimulation.run(design, 3_000, 3);

            assertThat(report.consistent()).isTrue();
            assertThat(report.duplicatesSkipped()).isPositive();
        }
    }

    @Test
    void cdcLeavesNoOutboxRowsBehind() {
        assertThat(OutboxSimulation.run(Design.OUTBOX_CDC_INBOX, 1_000, 3).outboxRowsLeft()).isZero();
    }
}
