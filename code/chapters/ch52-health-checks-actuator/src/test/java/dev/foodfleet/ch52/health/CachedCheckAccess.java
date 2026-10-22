package dev.foodfleet.ch52.health;

import java.time.Duration;
import java.util.function.LongSupplier;

/** Gives tests the clock-taking constructor. */
public final class CachedCheckAccess {

    public HealthCheck create(HealthCheck delegate, Duration ttl, LongSupplier clock) {
        return new CachedCheck(delegate, ttl, clock);
    }
}
