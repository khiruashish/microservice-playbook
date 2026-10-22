package dev.foodfleet.interview;

import java.time.Duration;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * A circuit breaker as asked for in coding rounds: CLOSED counts outcomes over the last {@code window} calls and opens
 * when the failure rate reaches the threshold (after at least {@code minimumCalls}); OPEN rejects at once until
 * {@code openFor} has passed; HALF_OPEN lets {@code trialCalls} through and closes if they all succeed, else reopens.
 * Thread-safe; the clock is injectable so tests don't sleep. (Resilience4j adds time-based windows, slow-call rates
 * and metrics: chapter 43.)
 */
public final class CircuitBreaker {

    public enum State { CLOSED, OPEN, HALF_OPEN }

    public static final class OpenException extends RuntimeException {
        OpenException() {
            super("circuit open", null, false, false);
        }
    }

    private final int window;
    private final int minimumCalls;
    private final double failureRateThreshold;
    private final long openForNanos;
    private final int trialCalls;
    private final LongSupplier clock;

    private final boolean[] outcomes;  // ring buffer of the last calls: true = failure
    private int recorded;
    private int next;
    private int failures;
    private State state = State.CLOSED;
    private long openedAt;
    private int trialsStarted;
    private int trialsSucceeded;

    public CircuitBreaker(int window, int minimumCalls, double failureRateThreshold, Duration openFor, int trialCalls,
                          LongSupplier nanoClock) {
        if (window < 1 || minimumCalls < 1 || minimumCalls > window || trialCalls < 1
                || !(failureRateThreshold > 0 && failureRateThreshold <= 1)) {
            throw new IllegalArgumentException("invalid circuit breaker settings");
        }
        this.window = window;
        this.minimumCalls = minimumCalls;
        this.failureRateThreshold = failureRateThreshold;
        this.openForNanos = openFor.toNanos();
        this.trialCalls = trialCalls;
        this.clock = nanoClock;
        this.outcomes = new boolean[window];
    }

    public <T> T call(Supplier<T> call) {
        acquirePermission();
        boolean failed = true;
        try {
            T result = call.get();
            failed = false;
            return result;
        } finally {
            record(failed);
        }
    }

    public synchronized State state() {
        if (state == State.OPEN && clock.getAsLong() - openedAt >= openForNanos) {
            state = State.HALF_OPEN;
            trialsStarted = 0;
            trialsSucceeded = 0;
        }
        return state;
    }

    private synchronized void acquirePermission() {
        switch (state()) {
            case OPEN -> throw new OpenException();
            case HALF_OPEN -> {
                if (trialsStarted >= trialCalls) {
                    throw new OpenException();   // trial calls already in flight
                }
                trialsStarted++;
            }
            case CLOSED -> { }
        }
    }

    private synchronized void record(boolean failed) {
        if (state == State.HALF_OPEN) {
            if (failed) {
                open();
            } else if (++trialsSucceeded == trialCalls) {
                close();
            }
            return;
        }
        if (state == State.OPEN) {
            return;                              // a call that started before the breaker opened
        }
        if (recorded == window && outcomes[next]) {
            failures--;                          // the outcome falling out of the window
        }
        outcomes[next] = failed;
        if (failed) {
            failures++;
        }
        next = (next + 1) % window;
        recorded = Math.min(recorded + 1, window);
        if (recorded >= minimumCalls && (double) failures / recorded >= failureRateThreshold) {
            open();
        }
    }

    private void open() {
        state = State.OPEN;
        openedAt = clock.getAsLong();
    }

    private void close() {
        state = State.CLOSED;
        recorded = 0;
        next = 0;
        failures = 0;
        java.util.Arrays.fill(outcomes, false);
    }
}
