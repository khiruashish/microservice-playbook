package dev.foodfleet.ch14.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import dev.foodfleet.ch14.saga.SagaStatus;
import org.junit.jupiter.api.Test;

class SagaSimulationTest {

    final SagaSimulation simulation = new SagaSimulation();

    @Test
    void withIdempotentParticipantsMoneyAndCouponsAddUp() {
        SagaSimulation.Report r = simulation.run(10_000, true, 0.005, 7);

        assertThat(r.crashes()).isPositive();
        assertThat(r.firstPass()).containsKey(SagaStatus.STUCK);
        assertThat(r.afterRetry()).doesNotContainKey(SagaStatus.STUCK);
        assertThat(r.extraHeldPaise()).isZero();
        assertThat(r.couponsLeaked()).isZero();
        assertThat(r.approvedWithoutKitchen()).isZero();
    }

    @Test
    void aNonIdempotentPaymentServiceDoubleCharges() {
        assertThat(simulation.run(10_000, false, 0.005, 7).extraHeldPaise()).isPositive();
    }
}
