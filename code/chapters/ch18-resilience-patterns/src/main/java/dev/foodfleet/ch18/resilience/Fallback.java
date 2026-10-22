package dev.foodfleet.ch18.resilience;

import java.util.function.Consumer;

/**
 * Fallback: when the protected call fails for any reason, answer with something less good but
 * useful (here: the last known ETA, marked as approximate) instead of an error.
 */
public final class Fallback implements Call {

    private final Call delegate;

    public Fallback(Call delegate) {
        this.delegate = delegate;
    }

    @Override
    public void start(Consumer<Outcome> done) {
        delegate.start(outcome -> done.accept(outcome.isFailure() ? Outcome.FALLBACK : outcome));
    }
}
