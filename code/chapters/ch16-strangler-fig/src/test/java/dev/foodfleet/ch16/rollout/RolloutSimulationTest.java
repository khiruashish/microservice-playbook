package dev.foodfleet.ch16.rollout;

import static org.assertj.core.api.Assertions.assertThat;

import dev.foodfleet.ch16.pricing.NewPricing;
import dev.foodfleet.ch16.rollout.RolloutSimulation.Strategy;
import org.junit.jupiter.api.Test;

class RolloutSimulationTest {

    @Test
    void shadowTrafficFindsEveryBugBeforeACustomerSeesIt() {
        RolloutSimulation.ShadowResult v1 = RolloutSimulation.shadow(NewPricing.Release.V1, 10_000, 1);
        RolloutSimulation.ShadowResult v3 = RolloutSimulation.shadow(NewPricing.Release.V3, 10_000, 1);

        assertThat(v1.mismatch()).isPositive();
        assertThat(v1.mismatchesByField()).containsKeys("deliveryFee", "surcharge");
        assertThat(v3.mismatch()).isZero();
        assertThat(v3.withinTolerance()).isPositive();      // the monolith's double rounding, accepted
    }

    @Test
    void aCanaryLimitsTheBlastRadiusAndFallbackHidesIt() {
        RolloutSimulation.RolloutResult bigBang = RolloutSimulation.rollout(Strategy.BIG_BANG, 1);
        RolloutSimulation.RolloutResult canary = RolloutSimulation.rollout(Strategy.CANARY, 1);
        RolloutSimulation.RolloutResult fallback = RolloutSimulation.rollout(Strategy.CANARY_WITH_FALLBACK, 1);

        assertThat(canary.customersAffected()).isLessThan(bigBang.customersAffected());
        assertThat(canary.rolledBackAtMinute()).isPositive();
        assertThat(fallback.customerErrors()).isZero();
        assertThat(fallback.rolledBackAtMinute()).isEqualTo(canary.rolledBackAtMinute());   // the guard still sees it
    }

    @Test
    void theGuardNeedsEnoughTraffic() {
        int[] served = new int[60];
        int[] failed = new int[60];
        served[0] = 150;
        failed[0] = 150;

        assertThat(RolloutSimulation.guardTrips(served, failed, 0)).isFalse();   // 100% failing, but only 150
        served[1] = 60;
        assertThat(RolloutSimulation.guardTrips(served, failed, 1)).isTrue();
    }
}
