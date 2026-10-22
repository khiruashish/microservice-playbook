package dev.foodfleet.ch52.health;

import java.time.Duration;
import java.util.function.LongSupplier;

/**
 * Remembers a check's result for a while, so probes from the kubelet, the load balancer and monitoring don't each
 * cost a database round trip (Spring Boot: {@code management.endpoint.health.cache.time-to-live}, 0 by default).
 */
public final class CachedCheck implements HealthCheck {

    private final HealthCheck delegate;
    private final long ttlNanos;
    private final LongSupplier clock;
    private Result cached;
    private long expires;

    public CachedCheck(HealthCheck delegate, Duration ttl) {
        this(delegate, ttl, System::nanoTime);
    }

    CachedCheck(HealthCheck delegate, Duration ttl, LongSupplier clock) {
        this.delegate = delegate;
        this.ttlNanos = ttl.toNanos();
        this.clock = clock;
    }

    @Override
    public synchronized Result check() throws Exception {
        long now = clock.getAsLong();
        if (cached == null || now - expires >= 0) {
            cached = delegate.check();
            expires = now + ttlNanos;
        }
        return cached;
    }
}
