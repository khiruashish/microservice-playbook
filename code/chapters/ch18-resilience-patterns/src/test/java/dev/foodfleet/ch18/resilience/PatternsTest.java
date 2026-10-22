package dev.foodfleet.ch18.resilience;

import static org.assertj.core.api.Assertions.assertThat;

import dev.foodfleet.ch18.sim.EventLoop;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class PatternsTest {

    private final EventLoop loop = new EventLoop();
    private final ScriptedCall dependency = new ScriptedCall(loop);
    private final List<Outcome> outcomes = new ArrayList<>();
    private final List<Long> finishedAt = new ArrayList<>();

    private void call(Call call) {
        call.start(outcome -> {
            outcomes.add(outcome);
            finishedAt.add(loop.now());
        });
    }

    @Test
    void aTimeoutAnswersAtTheLimitEvenIfTheDependencyIsSlower() {
        dependency.then(Outcome.SUCCESS, 2_500);

        call(new Timeout(loop, dependency, EventLoop.ms(300)));
        loop.runUntil(EventLoop.seconds(5));

        assertThat(outcomes).containsExactly(Outcome.TIMEOUT);
        assertThat(finishedAt).containsExactly(EventLoop.ms(300));
    }

    @Test
    void retryTriesAgainAfterTransientFailuresOnly() {
        dependency.then(Outcome.UNAVAILABLE, 5).then(Outcome.TIMEOUT, 5).then(Outcome.SUCCESS, 5);

        call(new Retry(loop, dependency, 3, EventLoop.ms(10), new Random(1)));
        loop.runUntil(EventLoop.seconds(1));

        assertThat(outcomes).containsExactly(Outcome.SUCCESS);
        assertThat(dependency.calls).isEqualTo(3);
    }

    @Test
    void retryDoesNotRetryALocalRejection() {
        dependency.then(Outcome.CIRCUIT_OPEN, 0);

        call(new Retry(loop, dependency, 3, 0, new Random(1)));
        loop.runUntil(EventLoop.seconds(1));

        assertThat(outcomes).containsExactly(Outcome.CIRCUIT_OPEN);
        assertThat(dependency.calls).isEqualTo(1);
    }

    @Test
    void theBulkheadRejectsBeyondItsLimitAtOnce() {
        dependency.otherwise(Outcome.SUCCESS, 1_000);
        Bulkhead bulkhead = new Bulkhead(dependency, 2);

        call(bulkhead);
        call(bulkhead);
        call(bulkhead);                                   // third concurrent call

        assertThat(outcomes).containsExactly(Outcome.BULKHEAD_FULL);
        loop.runUntil(EventLoop.seconds(2));
        call(bulkhead);                                   // permits came back
        loop.runUntil(EventLoop.seconds(4));
        assertThat(outcomes).containsExactly(Outcome.BULKHEAD_FULL, Outcome.SUCCESS, Outcome.SUCCESS, Outcome.SUCCESS);
    }

    @Test
    void theBreakerOpensThenProbesThenCloses() {
        dependency.otherwise(Outcome.UNAVAILABLE, 1);
        CircuitBreaker breaker = new CircuitBreaker(loop, dependency, 10, 10, 50, EventLoop.seconds(5), 2);
        for (int i = 0; i < 10; i++) {
            call(breaker);
            loop.runUntil(loop.now() + EventLoop.ms(10));
        }
        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.OPEN);

        call(breaker);                                    // short-circuited: the dependency isn't called
        assertThat(dependency.calls).isEqualTo(10);
        assertThat(outcomes.getLast()).isEqualTo(Outcome.CIRCUIT_OPEN);

        dependency.otherwise(Outcome.SUCCESS, 1);         // the dependency recovers
        loop.runUntil(loop.now() + EventLoop.seconds(5));
        call(breaker);
        call(breaker);
        call(breaker);                                    // only 2 trial calls allowed while half-open
        assertThat(outcomes.getLast()).isEqualTo(Outcome.CIRCUIT_OPEN);
        loop.runUntil(loop.now() + EventLoop.ms(10));

        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(dependency.calls).isEqualTo(12);
    }

    @Test
    void aFailedTrialReopensTheCircuit() {
        dependency.otherwise(Outcome.UNAVAILABLE, 1);
        CircuitBreaker breaker = new CircuitBreaker(loop, dependency, 10, 10, 50, EventLoop.seconds(5), 2);
        for (int i = 0; i < 10; i++) {
            call(breaker);
            loop.runUntil(loop.now() + EventLoop.ms(10));
        }
        loop.runUntil(loop.now() + EventLoop.seconds(5));

        call(breaker);
        loop.runUntil(loop.now() + EventLoop.ms(10));

        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(breaker.timesOpened()).isEqualTo(2);
    }

    @Test
    void fallbackTurnsAnyFailureIntoADegradedAnswer() {
        dependency.then(Outcome.UNAVAILABLE, 1).then(Outcome.SUCCESS, 1);
        Fallback fallback = new Fallback(dependency);

        call(fallback);
        call(fallback);
        loop.runUntil(EventLoop.seconds(1));

        assertThat(outcomes).containsExactly(Outcome.FALLBACK, Outcome.SUCCESS);
    }
}
