package dev.foodfleet.ch21.paging;

import static org.assertj.core.api.Assertions.assertThat;

import dev.foodfleet.ch21.paging.PagingAnomalySimulation.Result;
import dev.foodfleet.ch21.paging.PagingAnomalySimulation.Strategy;
import org.junit.jupiter.api.Test;

class PagingAnomalySimulationTest {

    @Test
    void onAStableListBothStrategiesAreCorrect() {
        for (Strategy s : Strategy.values()) {
            Result r = PagingAnomalySimulation.run(s, 500, 20, 0, 0, 1);
            assertThat(r.duplicates()).isZero();
            assertThat(r.skipped()).isZero();
        }
    }

    @Test
    void arrivalsMakeOffsetPagingRepeatItems() {
        assertThat(PagingAnomalySimulation.run(Strategy.OFFSET, 500, 20, 3, 0, 1).duplicates()).isPositive();
    }

    @Test
    void removalsMakeOffsetPagingSkipItems() {
        assertThat(PagingAnomalySimulation.run(Strategy.OFFSET, 500, 20, 0, 3, 1).skipped()).isPositive();
    }

    @Test
    void keysetPagingIsUnaffectedByEither() {
        Result r = PagingAnomalySimulation.run(Strategy.KEYSET, 500, 20, 10, 10, 1);

        assertThat(r.duplicates()).isZero();
        assertThat(r.skipped()).isZero();
    }
}
