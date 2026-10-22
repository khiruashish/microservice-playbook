package dev.foodfleet.ch18.resilience;

import java.util.function.Consumer;

/**
 * Bulkhead: at most {@code limit} calls in flight to this dependency; beyond that, reject at once.
 * Named after a ship's watertight compartments: a flood in one doesn't sink the others. Here it
 * means a slow maps provider can hold at most {@code limit} of tracking-service's workers.
 */
public final class Bulkhead implements Call {

    private final Call delegate;
    private final int limit;
    private int inFlight;
    private long rejected;

    public Bulkhead(Call delegate, int limit) {
        this.delegate = delegate;
        this.limit = limit;
    }

    @Override
    public void start(Consumer<Outcome> done) {
        if (inFlight >= limit) {
            rejected++;
            done.accept(Outcome.BULKHEAD_FULL);
            return;
        }
        inFlight++;
        delegate.start(outcome -> {
            inFlight--;
            done.accept(outcome);
        });
    }

    public long rejected() {
        return rejected;
    }
}
