package dev.foodfleet.ch17.ambassador;

import java.time.Duration;
import java.util.function.LongSupplier;

/**
 * A deliberately small breaker: after {@code threshold} consecutive failures it opens for
 * {@code openFor}; then one trial call is let through (half-open). Success closes it.
 */
public final class CircuitBreaker {

    public enum State { CLOSED, OPEN, HALF_OPEN }

    private final int threshold;
    private final long openForNanos;
    private final LongSupplier clock;
    private int consecutiveFailures;
    private long openedAt;
    private State state = State.CLOSED;
    private boolean trialInFlight;

    public CircuitBreaker(int threshold, Duration openFor, LongSupplier nanoClock) {
        this.threshold = threshold;
        this.openForNanos = openFor.toNanos();
        this.clock = nanoClock;
    }

    /** May a call go to the upstream now? */
    public synchronized boolean allow() {
        if (state == State.OPEN && clock.getAsLong() - openedAt >= openForNanos) {
            state = State.HALF_OPEN;
            trialInFlight = false;
        }
        return switch (state) {
            case CLOSED -> true;
            case OPEN -> false;
            case HALF_OPEN -> {
                if (trialInFlight) {
                    yield false;
                }
                trialInFlight = true;
                yield true;
            }
        };
    }

    public synchronized void success() {
        consecutiveFailures = 0;
        state = State.CLOSED;
        trialInFlight = false;
    }

    public synchronized void failure() {
        consecutiveFailures++;
        if (state == State.HALF_OPEN || consecutiveFailures >= threshold) {
            state = State.OPEN;
            openedAt = clock.getAsLong();
            trialInFlight = false;
        }
    }

    /** The state as an observer would see it now (does not consume the half-open trial). */
    public synchronized State state() {
        if (state == State.OPEN && clock.getAsLong() - openedAt >= openForNanos) {
            return State.HALF_OPEN;
        }
        return state;
    }
}
