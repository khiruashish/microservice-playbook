package dev.foodfleet.ch44.budget;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class TimeoutBudgetTest {

    private final TimeoutBudget budget = new TimeoutBudget(Duration.ofMillis(150), Duration.ofMillis(20), Duration.ofMillis(20));

    @Test
    void plentyOfTimeLeftMeansTheHopsOwnCeiling() {
        assertThat(budget.next(Duration.ofMillis(800))).contains(Duration.ofMillis(150));
    }

    @Test
    void littleTimeLeftMeansWhatIsLeftMinusTheReserve() {
        assertThat(budget.next(Duration.ofMillis(100))).contains(Duration.ofMillis(80));
    }

    @Test
    void tooLittleTimeLeftMeansNoCallAtAll() {
        assertThat(budget.next(Duration.ofMillis(35))).isEmpty();
        assertThat(budget.next(Duration.ofMillis(-5))).isEmpty();
    }
}
