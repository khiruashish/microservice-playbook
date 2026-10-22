package dev.foodfleet.ch44.degrade;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BrownoutTest {

    private final Brownout brownout = new Brownout(0.1, 0.05);

    private void interval(int good, int bad) {
        for (int i = 0; i < good; i++) {
            brownout.succeeded();
        }
        for (int i = 0; i < bad; i++) {
            brownout.failedOrSlow();
        }
        brownout.adjust();
    }

    @Test
    void halvesWhenTooManyCallsAreSlowDownToTheFloor() {
        interval(50, 50);
        assertThat(brownout.level()).isEqualTo(0.5);
        for (int i = 0; i < 10; i++) {
            interval(5, 5);
        }
        assertThat(brownout.level()).isEqualTo(0.05);
    }

    @Test
    void recoversStepByStepWhenCallsAreFastAgain() {
        interval(0, 10);
        interval(0, 10);
        assertThat(brownout.level()).isEqualTo(0.25);
        interval(10, 0);
        assertThat(brownout.level()).isGreaterThan(0.34);
        assertThat(brownout.level()).isLessThan(0.36);
    }

    @Test
    void anIntervalWithoutCallsChangesNothing() {
        interval(0, 10);
        brownout.adjust();
        assertThat(brownout.level()).isEqualTo(0.5);
    }

    @Test
    void theLevelIsTheShareOfCallersThatTry() {
        interval(0, 10);
        int tried = 0;
        for (int i = 0; i < 20_000; i++) {
            if (brownout.shouldTry()) {
                tried++;
            }
        }
        assertThat(tried).isBetween(9_400, 10_600);
    }
}
