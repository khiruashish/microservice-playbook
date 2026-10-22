package dev.foodfleet.ch18.resilience;

import dev.foodfleet.ch18.sim.EventLoop;
import java.util.Random;
import java.util.function.Consumer;

/**
 * Retry: try again after a transient failure (timeout, unavailable), up to {@code maxAttempts},
 * waiting {@code backoff × 2^(n-1)} with full jitter between attempts. Rejections by our own
 * bulkhead or circuit breaker are not retried: they mean "stop", not "try again".
 */
public final class Retry implements Call {

    private final EventLoop loop;
    private final Call delegate;
    private final int maxAttempts;
    private final long backoff;
    private final Random random;
    private long retries;

    public Retry(EventLoop loop, Call delegate, int maxAttempts, long backoffMicros, Random random) {
        this.loop = loop;
        this.delegate = delegate;
        this.maxAttempts = maxAttempts;
        this.backoff = backoffMicros;
        this.random = random;
    }

    @Override
    public void start(Consumer<Outcome> done) {
        attempt(1, done);
    }

    private void attempt(int n, Consumer<Outcome> done) {
        delegate.start(outcome -> {
            boolean transientFailure = outcome == Outcome.TIMEOUT || outcome == Outcome.UNAVAILABLE;
            if (!transientFailure || n >= maxAttempts) {
                done.accept(outcome);
                return;
            }
            retries++;
            long ceiling = backoff << (n - 1);
            long wait = ceiling == 0 ? 0 : (long) (random.nextDouble() * ceiling);   // full jitter
            loop.after(wait, () -> attempt(n + 1, done));
        });
    }

    public long retries() {
        return retries;
    }
}
