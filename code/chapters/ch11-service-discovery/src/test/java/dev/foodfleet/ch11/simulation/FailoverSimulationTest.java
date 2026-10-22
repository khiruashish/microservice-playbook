package dev.foodfleet.ch11.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.Test;

class FailoverSimulationTest {

    private final FailoverSimulation simulation = new FailoverSimulation();
    private final FailoverSimulation.Scenario scenario = new FailoverSimulation.Scenario(100, 4, 200_000, 42);

    @Test
    void eurekaDefaultsTakeFourMinutesOnAverage() {
        // 165 s (2 × 90 − half a heartbeat) + 30 + 15 + 15 + 17.5 = 242.5 s
        FailoverSimulation.Result r = simulation.run(Mechanisms.eurekaDefaults(), scenario);

        assertThat(r.meanSeconds()).isCloseTo(242.5, within(0.5));
        assertThat(r.failedRequests()).isCloseTo(25 * 242.5, within(15.0));
    }

    @Test
    void kubernetesNoticesACrashInSecondsButAHangOnlyThroughReadinessProbes() {
        assertThat(simulation.run(Mechanisms.kubernetesCrash(), scenario).meanSeconds()).isCloseTo(3.0, within(1e-9));
        assertThat(simulation.run(Mechanisms.kubernetesHang(), scenario).meanSeconds()).isCloseTo(27.0, within(0.1));
    }

    @Test
    void retryingOnAnotherInstanceTurnsFailuresIntoSlowRequests() {
        FailoverSimulation.Result r = simulation.run(Mechanisms.kubernetesHang().withRetries(), scenario);

        assertThat(r.failedRequests()).isZero();
        assertThat(r.slowedRequests()).isCloseTo(25 * 27.0, within(5.0));
    }

    @Test
    void stagesBehaveAsDocumented() {
        Mechanism oneStage = new Mechanism("periodic only", List.of(new Stage.Periodic("tick", 10)), false);
        FailoverSimulation.Result r = simulation.run(oneStage, scenario);

        assertThat(r.meanSeconds()).isCloseTo(5.0, within(0.05));
        assertThat(r.p99Seconds()).isCloseTo(9.9, within(0.05));
    }
}
