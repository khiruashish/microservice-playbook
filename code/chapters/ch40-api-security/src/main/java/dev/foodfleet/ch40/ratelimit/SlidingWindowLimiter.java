package dev.foodfleet.ch40.ratelimit;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * The sliding-window counter: the current window's count plus the previous window's count weighted by how much of it
 * still overlaps the last {@code window}. Two counters per key, and no boundary burst, at the price of assuming the
 * previous window's requests were spread evenly.
 */
public final class SlidingWindowLimiter implements RateLimiter {

    private record Counts(long index, int current, int previous) {
    }

    private final String name;
    private final int limit;
    private final long windowNanos;
    private final ConcurrentMap<String, Counts> counts = new ConcurrentHashMap<>();

    public SlidingWindowLimiter(String name, int limit, Duration window) {
        this.name = name;
        this.limit = limit;
        this.windowNanos = window.toNanos();
    }

    @Override
    public Decision tryAcquire(String key, long nowNanos) {
        long index = Math.floorDiv(nowNanos, windowNanos);
        double intoWindow = (double) (nowNanos - index * windowNanos) / windowNanos;
        boolean[] allowed = new boolean[1];
        double[] estimate = new double[1];
        counts.compute(key, (k, old) -> {
            Counts c;
            if (old == null || old.index() < index - 1) {
                c = new Counts(index, 0, 0);
            } else if (old.index() == index - 1) {
                c = new Counts(index, 0, old.current());
            } else {
                c = old;
            }
            double weighted = c.previous() * (1 - intoWindow) + c.current();
            if (weighted + 1 <= limit) {
                allowed[0] = true;
                estimate[0] = weighted + 1;
                return new Counts(index, c.current() + 1, c.previous());
            }
            estimate[0] = weighted;
            return c;
        });
        Duration resetIn = Duration.ofNanos((index + 1) * windowNanos - nowNanos);
        if (allowed[0]) {
            return Decision.allow((long) Math.floor(limit - estimate[0]), resetIn);
        }
        // conservative: by the next window boundary the previous window has rotated out further
        return Decision.deny(Duration.ofNanos(Math.max(windowNanos / limit, 1)), resetIn);
    }

    @Override
    public Policy policy() {
        return new Policy(name, limit, Duration.ofNanos(windowNanos));
    }
}
