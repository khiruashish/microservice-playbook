package dev.foodfleet.interview;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Token-bucket rate limiting per key (a client, a tenant): each key's bucket holds up to {@code capacity} tokens and
 * refills at {@code perSecond}; a request takes one token or is refused. Buckets refill lazily from the elapsed time,
 * so idle keys cost nothing. In one JVM only: across instances the state must be shared, as FoodFleet does with GCRA in
 * Redis (chapter 40), or each instance enforces its share.
 */
public final class TokenBucket {

    private final double capacity;
    private final double perNano;
    private final LongSupplier clock;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    private static final class Bucket {
        double tokens;
        long updated;

        Bucket(double tokens, long updated) {
            this.tokens = tokens;
            this.updated = updated;
        }
    }

    public TokenBucket(double capacity, double perSecond, LongSupplier nanoClock) {
        if (capacity < 1 || perSecond <= 0) {
            throw new IllegalArgumentException("capacity >= 1 and a positive rate");
        }
        this.capacity = capacity;
        this.perNano = perSecond / 1e9;
        this.clock = nanoClock;
    }

    /** Takes a token for {@code key} if one is available. */
    public boolean tryAcquire(String key) {
        long now = clock.getAsLong();
        Bucket b = buckets.computeIfAbsent(key, k -> new Bucket(capacity, now));
        synchronized (b) {                       // one lock per key: keys don't contend with each other
            b.tokens = Math.min(capacity, b.tokens + (now - b.updated) * perNano);
            b.updated = now;
            if (b.tokens >= 1) {
                b.tokens -= 1;
                return true;
            }
            return false;
        }
    }

    /** Seconds until {@code key} will have a token: what a 429's Retry-After should say. */
    public double secondsUntilToken(String key) {
        Bucket b = buckets.get(key);
        if (b == null) {
            return 0;
        }
        synchronized (b) {
            double tokens = Math.min(capacity, b.tokens + (clock.getAsLong() - b.updated) * perNano);
            return tokens >= 1 ? 0 : (1 - tokens) / perNano / 1e9;
        }
    }
}
