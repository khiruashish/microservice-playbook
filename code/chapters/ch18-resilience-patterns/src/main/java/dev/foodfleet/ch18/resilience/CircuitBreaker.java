package dev.foodfleet.ch18.resilience;

import dev.foodfleet.ch18.sim.EventLoop;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

/**
 * Circuit breaker (count-based, like Resilience4j's default): looks at the last {@code window}
 * calls; if at least {@code minimumCalls} were recorded and {@code failureRatePercent} or more
 * failed, it OPENS: calls fail at once without touching the dependency, for {@code openFor}. Then
 * HALF_OPEN lets {@code trialCalls} through; if they all succeed it CLOSES, otherwise it opens again.
 */
public final class CircuitBreaker implements Call {

    public enum State { CLOSED, OPEN, HALF_OPEN }

    private final EventLoop loop;
    private final Call delegate;
    private final int window;
    private final int minimumCalls;
    private final int failureRatePercent;
    private final long openFor;
    private final int trialCalls;

    private final Deque<Boolean> results = new ArrayDeque<>();
    private State state = State.CLOSED;
    private long openedAt;
    private int trialsStarted;
    private int trialsSucceeded;
    private long shortCircuited;
    private int timesOpened;

    public CircuitBreaker(EventLoop loop, Call delegate, int window, int minimumCalls, int failureRatePercent,
                          long openForMicros, int trialCalls) {
        this.loop = loop;
        this.delegate = delegate;
        this.window = window;
        this.minimumCalls = minimumCalls;
        this.failureRatePercent = failureRatePercent;
        this.openFor = openForMicros;
        this.trialCalls = trialCalls;
    }

    @Override
    public void start(Consumer<Outcome> done) {
        if (state == State.OPEN && loop.now() - openedAt >= openFor) {
            state = State.HALF_OPEN;
            trialsStarted = 0;
            trialsSucceeded = 0;
        }
        if (state == State.OPEN || (state == State.HALF_OPEN && trialsStarted >= trialCalls)) {
            shortCircuited++;
            done.accept(Outcome.CIRCUIT_OPEN);
            return;
        }
        boolean trial = state == State.HALF_OPEN;
        if (trial) {
            trialsStarted++;
        }
        delegate.start(outcome -> {
            record(outcome == Outcome.SUCCESS, trial);
            done.accept(outcome);
        });
    }

    private void record(boolean success, boolean trial) {
        if (trial && state == State.HALF_OPEN) {
            if (!success) {
                open();
            } else if (++trialsSucceeded == trialCalls) {
                state = State.CLOSED;
                results.clear();
            }
            return;
        }
        if (state != State.CLOSED) {
            return;                                   // a slow call from before the circuit opened
        }
        results.addLast(success);
        if (results.size() > window) {
            results.removeFirst();
        }
        long failures = results.stream().filter(ok -> !ok).count();
        if (results.size() >= minimumCalls && failures * 100 >= (long) failureRatePercent * results.size()) {
            open();
        }
    }

    private void open() {
        state = State.OPEN;
        openedAt = loop.now();
        timesOpened++;
    }

    public State state() {
        return state;
    }

    public long shortCircuited() {
        return shortCircuited;
    }

    public int timesOpened() {
        return timesOpened;
    }
}
