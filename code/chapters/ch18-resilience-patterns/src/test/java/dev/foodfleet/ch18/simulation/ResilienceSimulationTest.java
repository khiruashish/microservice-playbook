package dev.foodfleet.ch18.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import dev.foodfleet.ch18.simulation.ResilienceSimulation.Result;
import dev.foodfleet.ch18.simulation.ResilienceSimulation.Scenario;
import org.junit.jupiter.api.Test;

class ResilienceSimulationTest {

    private static Result run(Scenario scenario) {
        return ResilienceSimulation.run(scenario, 7);
    }

    @Test
    void withoutProtectionASlowDependencyTakesDownAnEndpointThatDoesntUseIt() {
        assertThat(run(Scenario.NOTHING).statusOk()).isLessThan(20);
    }

    @Test
    void aTimeoutAloneKeepsTheStatusEndpointUp() {
        assertThat(run(Scenario.TIMEOUT).statusOk()).isGreaterThan(99);
    }

    @Test
    void retriesMultiplyTheLoadOnTheStrugglingDependency() {
        assertThat(run(Scenario.TIMEOUT_RETRY).providerCallsPerEta())
                .isGreaterThan(2 * run(Scenario.TIMEOUT).providerCallsPerEta());
    }

    @Test
    void theBreakerTakesNearlyAllLoadOffTheDependency() {
        assertThat(run(Scenario.TIMEOUT_BULKHEAD_BREAKER).providerCallsPerEta()).isLessThan(0.1);
    }

    @Test
    void withFallbackNoEtaRequestFails() {
        Result all = run(Scenario.ALL);

        assertThat(all.etaFailed()).isLessThan(0.5);
        assertThat(all.statusOk()).isGreaterThan(99);
    }
}
