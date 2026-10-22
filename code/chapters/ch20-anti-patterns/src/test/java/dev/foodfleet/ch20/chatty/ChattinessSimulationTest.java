package dev.foodfleet.ch20.chatty;

import static org.assertj.core.api.Assertions.assertThat;

import dev.foodfleet.ch20.chatty.ChattinessSimulation.Strategy;
import org.junit.jupiter.api.Test;

class ChattinessSimulationTest {

    @Test
    void nPlusOneIsSlowerAndLessReliableThanOneBatch() {
        var sequential = ChattinessSimulation.run(Strategy.SEQUENTIAL, 12, 20_000, 1);
        var parallel = ChattinessSimulation.run(Strategy.PARALLEL, 12, 20_000, 1);
        var batch = ChattinessSimulation.run(Strategy.BATCH, 12, 20_000, 1);

        assertThat(sequential.p99Ms()).isGreaterThan(2 * batch.p99Ms());
        assertThat(parallel.p99Ms()).isGreaterThan(batch.p99Ms());           // waits for the slowest of 12
        assertThat(sequential.failedPercent()).isGreaterThan(5 * batch.failedPercent());
    }
}
