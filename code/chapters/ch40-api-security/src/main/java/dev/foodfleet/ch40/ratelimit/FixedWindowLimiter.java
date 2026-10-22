package dev.foodfleet.ch40.ratelimit;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * At most {@code limit} requests per key in each fixed window (12:00:00–12:00:59, 12:01:00–…). Simple and cheap,
 * but a client can send {@code limit} requests at the end of one window and {@code limit} more at the start of the
 * next: twice the limit within a second or two. Kept here as the baseline the others are measured against.
 */
public final class FixedWindowLimiter implements RateLimiter {

    private record Window(long index, int count) {
    }

    private final String name;
    private final int limit;
    private final long windowNanos;
    private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();

    public FixedWindowLimiter(String name, int limit, Duration window) {
        this.name = name;
        this.limit = limit;
        this.windowNanos = window.toNanos();
    }

    @Override
    public Decision tryAcquire(String key, long nowNanos) {
        long index = Math.floorDiv(nowNanos, windowNanos);
        Duration resetIn = Duration.ofNanos((index + 1) * windowNanos - nowNanos);
        boolean[] allowed = new boolean[1];
        Window w = windows.compute(key, (k, old) -> {
            Window current = old == null || old.index() != index ? new Window(index, 0) : old;
            if (current.count() < limit) {
                allowed[0] = true;
                return new Window(index, current.count() + 1);
            }
            return current;
        });
        return allowed[0] ? Decision.allow(limit - w.count(), resetIn) : Decision.deny(resetIn, resetIn);
    }

    @Override
    public Policy policy() {
        return new Policy(name, limit, Duration.ofNanos(windowNanos));
    }
}
