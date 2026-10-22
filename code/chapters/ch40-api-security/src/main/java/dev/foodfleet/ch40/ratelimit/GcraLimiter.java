package dev.foodfleet.ch40.ratelimit;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A token bucket in its GCRA form (the generic cell rate algorithm): one number per key, the "theoretical arrival
 * time" (TAT) of the next request. Requests arrive at most every {@code interval = window / limit}; up to
 * {@code burst} may come at once. Equivalent to a bucket of {@code burst} tokens refilled at {@code limit/window},
 * without a timer or a separate token count. The same arithmetic runs in Redis ({@code ratelimit/gcra.lua}) when
 * several gateway instances must share one limit.
 */
public final class GcraLimiter implements RateLimiter {

    private final String name;
    private final int limit;
    private final long windowNanos;
    private final long intervalNanos;
    private final long burstToleranceNanos;
    private final ConcurrentMap<String, Long> tat = new ConcurrentHashMap<>();

    /** @param burst how many requests may arrive back to back (the bucket size), at least 1 */
    public GcraLimiter(String name, int limit, Duration window, int burst) {
        if (limit < 1 || burst < 1) {
            throw new IllegalArgumentException("limit and burst must be positive");
        }
        this.name = name;
        this.limit = limit;
        this.windowNanos = window.toNanos();
        this.intervalNanos = windowNanos / limit;
        this.burstToleranceNanos = intervalNanos * (burst - 1L);
    }

    @Override
    public Decision tryAcquire(String key, long nowNanos) {
        long[] result = new long[2];          // [allowed 0/1, tat after]
        tat.compute(key, (k, old) -> {
            long current = old == null ? nowNanos : Math.max(old, nowNanos);
            long allowAt = current - burstToleranceNanos;
            if (nowNanos < allowAt) {
                result[0] = 0;
                result[1] = current;
                return current;
            }
            result[0] = 1;
            result[1] = current + intervalNanos;
            return result[1];
        });
        long newTat = result[1];
        Duration resetIn = Duration.ofNanos(Math.max(0, newTat - nowNanos));
        if (result[0] == 1) {
            long remaining = (burstToleranceNanos - (newTat - nowNanos - intervalNanos)) / intervalNanos;
            return Decision.allow(Math.max(0, remaining), resetIn);
        }
        return Decision.deny(Duration.ofNanos(newTat - burstToleranceNanos - nowNanos), resetIn);
    }

    /**
     * Forgets keys whose bucket is full again (TAT in the past): they behave exactly like keys never seen. Without
     * this the map grows with every client and IP address that ever called.
     */
    public int evictIdle(long nowNanos) {
        int before = tat.size();
        tat.values().removeIf(t -> t <= nowNanos);
        return before - tat.size();
    }

    public int size() {
        return tat.size();
    }

    @Override
    public Policy policy() {
        return new Policy(name, limit, Duration.ofNanos(windowNanos));
    }
}
