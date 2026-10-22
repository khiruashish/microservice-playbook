package dev.foodfleet.ch03.replication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.foodfleet.ch03.clock.HybridLogicalClock;
import dev.foodfleet.ch03.replication.QuorumRegister.Mode;
import dev.foodfleet.ch03.replication.QuorumRegister.WriteResult;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/** An order's status replicated across three replicas, under different N/W/R settings and failures. */
class QuorumRegisterTest {

    private final AtomicLong wall = new AtomicLong(1_000);
    private final HybridLogicalClock clock = new HybridLogicalClock(() -> wall.incrementAndGet());

    @Test
    void nonOverlappingQuorums_readYourOwnWriteFails() {
        QuorumRegister<String> status = new QuorumRegister<>(3, 1, 1, Mode.CP, clock);
        status.write("PLACED");
        status.replicate();

        status.write("PREPARING");                              // acknowledged by replica 0 only

        assertThat(status.isStronglyConsistentConfiguration()).isFalse();
        assertThat(status.read().value()).contains("PLACED");   // read hit replica 2: stale
    }

    @Test
    void overlappingQuorums_RPlusWGreaterThanN_alwaysSeeLatestWrite() {
        QuorumRegister<String> status = new QuorumRegister<>(3, 2, 2, Mode.CP, clock);
        status.write("PLACED");
        status.replicate();

        status.write("PREPARING");                              // replicas 0 and 1

        assertThat(status.isStronglyConsistentConfiguration()).isTrue();
        assertThat(status.read().consulted()).containsExactly(2, 1); // overlap at replica 1
        assertThat(status.read().value()).contains("PREPARING");
    }

    @Test
    void cp_refusesWhenAQuorumIsUnreachable() {
        QuorumRegister<String> status = new QuorumRegister<>(3, 2, 2, Mode.CP, clock);
        status.partition(1, 2);

        assertThatThrownBy(() -> status.write("PREPARING")).isInstanceOf(QuorumUnavailableException.class);
        assertThatThrownBy(status::read).isInstanceOf(QuorumUnavailableException.class);
    }

    @Test
    void ap_staysAvailableDuringPartition_thenConverges() {
        QuorumRegister<String> status = new QuorumRegister<>(3, 2, 2, Mode.AP, clock);
        status.write("PLACED");
        status.replicate();

        status.partition(1, 2);
        WriteResult duringPartition = status.write("PREPARING");
        assertThat(duringPartition.acknowledgedBy()).containsExactly(0);
        assertThat(duringPartition.belowWriteQuorum()).isTrue();

        status.heal();
        status.partition(0);                                    // the other side of the split answers reads
        assertThat(status.read().value()).as("available, but stale").contains("PLACED");

        status.heal();
        status.replicate();                                     // hinted handoff / anti-entropy
        assertThat(status.replicaValues()).containsOnly(Optional.of("PREPARING"));
        assertThat(status.read().value()).contains("PREPARING");
    }

    @Test
    void replayingReplicationIsHarmless_lastWriteWins() {
        QuorumRegister<String> status = new QuorumRegister<>(3, 3, 1, Mode.CP, clock);
        status.write("PLACED");
        status.write("PREPARING");
        status.replicate();
        status.replicate();

        assertThat(status.replicaValues())
                .isEqualTo(List.of(Optional.of("PREPARING"), Optional.of("PREPARING"), Optional.of("PREPARING")));
    }
}
