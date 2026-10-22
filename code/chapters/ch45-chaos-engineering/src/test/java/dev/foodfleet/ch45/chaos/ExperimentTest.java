package dev.foodfleet.ch45.chaos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ExperimentTest {

    private static final Window GOOD = new Window(100, 100, 50);
    private static final Window BAD = new Window(100, 70, 2000);
    private static final Hypothesis SLO = new Hypothesis("99% succeed within 300 ms", 0.99, 300, 20);

    private final AtomicInteger injected = new AtomicInteger();
    private final AtomicInteger rolledBack = new AtomicInteger();

    private Experiment experiment(List<Window> windows, int abortAfter) {
        Deque<Window> script = new ArrayDeque<>(windows);
        return Experiment.named("menu latency")
                .hypothesis(SLO)
                .method(injected::incrementAndGet, rolledBack::incrementAndGet)
                .observe(() -> script.isEmpty() ? GOOD : script.poll())
                .baseline(2).duration(5).abortAfter(abortAfter).recoveryWithin(5)
                .build();
    }

    @Test
    void aToleratedFaultLeavesTheSteadyStateIntact() {
        Experiment.Report r = experiment(List.of(GOOD, GOOD, GOOD, GOOD, GOOD, GOOD, GOOD), 3).run();
        assertThat(r.verdict()).isEqualTo(Experiment.Verdict.HELD);
        assertThat(r.during()).hasSize(5);
        assertThat(rolledBack.get()).isEqualTo(1);
    }

    @Test
    void theGuardRollsBackAfterConsecutiveFailingWindows() {
        Experiment.Report r = experiment(List.of(GOOD, GOOD, GOOD, BAD, BAD, BAD, BAD), 3).run();
        assertThat(r.verdict()).isEqualTo(Experiment.Verdict.ABORTED);
        assertThat(r.windowsToAbort()).isEqualTo(4);
        assertThat(r.during()).hasSize(4);
        assertThat(rolledBack.get()).isEqualTo(1);
        assertThat(r.recovered()).isTrue();
    }

    @Test
    void withoutAGuardTheExperimentRunsToTheEnd() {
        Experiment.Report r = experiment(List.of(GOOD, GOOD, BAD, BAD, BAD, BAD, BAD), 0).run();
        assertThat(r.verdict()).isEqualTo(Experiment.Verdict.BROKEN);
        assertThat(r.failedWindows()).isEqualTo(5);
    }

    @Test
    void nothingIsInjectedIntoASystemThatIsAlreadyUnhealthy() {
        Experiment.Report r = experiment(List.of(GOOD, BAD), 3).run();
        assertThat(r.verdict()).isEqualTo(Experiment.Verdict.NOT_RUN);
        assertThat(injected.get()).isEqualTo(0);
        assertThat(rolledBack.get()).isEqualTo(0);
    }

    @Test
    void aSystemThatDoesNotComeBackIsReported() {
        Deque<Window> script = new ArrayDeque<>(List.of(GOOD, GOOD));
        Experiment.Report r = Experiment.named("stuck")
                .hypothesis(SLO)
                .method(() -> { }, () -> { })
                .observe(() -> script.isEmpty() ? BAD : script.poll())
                .baseline(2).duration(3).abortAfter(0).recoveryWithin(4)
                .build().run();
        assertThat(r.recovered()).isFalse();
        assertThat(r.windowsToRecover()).isEqualTo(-1);
    }

    @Test
    void theRollbackRunsEvenWhenInjectionFails() {
        Experiment e = Experiment.named("broken injector")
                .hypothesis(SLO)
                .method(() -> {
                    throw new IllegalStateException("toxiproxy down");
                }, rolledBack::incrementAndGet)
                .observe(() -> GOOD)
                .baseline(1).build();
        assertThatThrownBy(e::run).isInstanceOf(IllegalStateException.class);
        assertThat(rolledBack.get()).isEqualTo(1);
    }

    @Test
    void tooLittleTrafficIsNotEvidence() {
        assertThat(SLO.holds(new Window(5, 0, 5000))).isTrue();
        assertThat(SLO.holds(new Window(100, 98, 100))).isFalse();
        assertThat(SLO.holds(new Window(100, 100, 301))).isFalse();
    }
}
