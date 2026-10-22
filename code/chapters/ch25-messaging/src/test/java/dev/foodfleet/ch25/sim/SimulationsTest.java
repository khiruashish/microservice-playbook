package dev.foodfleet.ch25.sim;

import static org.assertj.core.api.Assertions.assertThat;

import dev.foodfleet.ch25.sim.RetryStrategies.Scenario;
import dev.foodfleet.ch25.sim.RetryStrategies.Strategy;
import org.junit.jupiter.api.Test;

/** The chapter's three tables, as assertions about what they show. */
class SimulationsTest {

    @Test
    void whenTheCommitHappensDecidesWhatACrashCosts() {
        var before = DeliverySemantics.run(DeliverySemantics.Mode.COMMIT_BEFORE_PROCESSING, 100_000, 15);
        var after = DeliverySemantics.run(DeliverySemantics.Mode.COMMIT_AFTER_EACH, 100_000, 15);
        var batch = DeliverySemantics.run(DeliverySemantics.Mode.COMMIT_EVERY_500, 100_000, 15);
        var idempotent = DeliverySemantics.run(DeliverySemantics.Mode.COMMIT_AFTER_EACH_IDEMPOTENT, 100_000, 15);

        assertThat(before.lost()).isGreaterThan(0);
        assertThat(before.duplicateEffects()).isEqualTo(0);
        assertThat(after.lost()).isEqualTo(0);
        assertThat(after.duplicateEffects()).isGreaterThan(0);
        assertThat(batch.duplicateEffects()).isGreaterThan(100 * after.duplicateEffects());
        assertThat(idempotent.lost() + idempotent.duplicateEffects()).isEqualTo(0);
        assertThat(idempotent.redeliveries()).isGreaterThan(0);                    // redelivered, applied once
    }

    @Test
    void parallelismWithinAPartitionBreaksOrderUnlessItIsPerKey() {
        var serial = OrderingSimulation.run(OrderingSimulation.Mode.ONE_THREAD_PER_PARTITION, 25);
        var pool = OrderingSimulation.run(OrderingSimulation.Mode.POOL_OF_8, 25);
        var lanes = OrderingSimulation.run(OrderingSimulation.Mode.KEY_ORDERED_LANES_8, 25);

        assertThat(serial.outOfOrder()).isEqualTo(0);
        assertThat(pool.outOfOrder()).isGreaterThan(100);
        assertThat(lanes.outOfOrder()).isEqualTo(0);
        assertThat(pool.eventsPerSecond()).isGreaterThan(5 * serial.eventsPerSecond());
        assertThat(lanes.eventsPerSecond()).isGreaterThan(5 * serial.eventsPerSecond());
    }

    @Test
    void retryingForeverStopsThePartitionAndSkippingLosesMessages() {
        var forever = RetryStrategies.run(Strategy.RETRY_FOREVER_BLOCKING, Scenario.FULL_OUTAGE);
        var skip = RetryStrategies.run(Strategy.DEFAULT_TEN_ATTEMPTS_THEN_SKIP, Scenario.FULL_OUTAGE);

        assertThat(forever.unprocessed()).isGreaterThan(15_000);
        assertThat(skip.lost()).isGreaterThan(1);                                  // good messages, not just the poison one
    }

    @Test
    void classificationAndBackoffLoseNothingAndRetryTopicsHelpPartialOutages() {
        var blocking = RetryStrategies.run(Strategy.BLOCKING_BACKOFF_THEN_DLT, Scenario.FULL_OUTAGE);
        var classified = RetryStrategies.run(Strategy.BLOCKING_BACKOFF_CLASSIFIED, Scenario.FULL_OUTAGE);
        var classifiedPartial = RetryStrategies.run(Strategy.BLOCKING_BACKOFF_CLASSIFIED, Scenario.PARTIAL_OUTAGE);
        var topicsPartial = RetryStrategies.run(Strategy.RETRY_TOPICS_CLASSIFIED, Scenario.PARTIAL_OUTAGE);

        for (var r : new RetryStrategies.Result[] {blocking, classified, classifiedPartial, topicsPartial}) {
            assertThat(r.lost()).isEqualTo(0);
            assertThat(r.goodInDlt()).isEqualTo(0);
            assertThat(r.poisonInDlt()).isEqualTo(1);
        }
        assertThat(classified.p99LatencyMs()).isLessThan(blocking.p99LatencyMs());
        assertThat(topicsPartial.p99LatencyMs()).isLessThan(classifiedPartial.p99LatencyMs() / 3);
        assertThat(topicsPartial.orderViolations()).isGreaterThan(classifiedPartial.orderViolations());
    }
}
