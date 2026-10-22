package dev.foodfleet.ch18.resilience;

import dev.foodfleet.ch18.sim.EventLoop;
import java.util.function.Consumer;

/**
 * Timeout: stop waiting after {@code limit}. The caller's thread is freed; the dependency is NOT
 * told and keeps working on the abandoned request, which is why timeouts alone don't protect the
 * dependency.
 */
public final class Timeout implements Call {

    private final EventLoop loop;
    private final Call delegate;
    private final long limit;

    public Timeout(EventLoop loop, Call delegate, long limitMicros) {
        this.loop = loop;
        this.delegate = delegate;
        this.limit = limitMicros;
    }

    @Override
    public void start(Consumer<Outcome> done) {
        boolean[] finished = {false};
        loop.after(limit, () -> {
            if (!finished[0]) {
                finished[0] = true;
                done.accept(Outcome.TIMEOUT);
            }
        });
        delegate.start(outcome -> {
            if (!finished[0]) {
                finished[0] = true;
                done.accept(outcome);
            }
        });
    }
}
