package dev.foodfleet.ch10.gateway;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Token bucket per client key, in memory. Each client may burst up to {@code capacity} requests;
 * tokens refill continuously at {@code refillPerMinute}.
 *
 * <p>Limits are <b>per gateway instance</b>: with 4 replicas a client can get up to 4× the limit.
 * That's acceptable for abuse protection at the edge; for exact global limits use a shared store
 * (Redis, or Bucket4j's distributed proxy managers, which Spring Cloud Gateway MVC supports).
 */
public final class TokenBucketRateLimiter {

    public record Decision(boolean allowed, long remaining, Duration retryAfter) {
    }

    private static final class Bucket {
        double tokens;
        long lastRefillNanos;

        Bucket(double tokens, long now) {
            this.tokens = tokens;
            this.lastRefillNanos = now;
        }
    }

    private final int capacity;
    private final double tokensPerNano;
    private final LongSupplier nanoClock;
    private final Map<String, Bucket> buckets;

    public TokenBucketRateLimiter(int capacity, int refillPerMinute, int maxClients, LongSupplier nanoClock) {
        if (capacity < 1 || refillPerMinute < 1 || maxClients < 1) {
            throw new IllegalArgumentException("capacity, refillPerMinute and maxClients must be positive");
        }
        this.capacity = capacity;
        this.tokensPerNano = refillPerMinute / (double) Duration.ofMinutes(1).toNanos();
        this.nanoClock = nanoClock;
        // Access-ordered map that forgets the least recently seen client: memory stays bounded
        // even when someone sprays requests from millions of addresses.
        this.buckets = new LinkedHashMap<>(1024, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Bucket> eldest) {
                return size() > maxClients;
            }
        };
    }

    public synchronized Decision tryAcquire(String clientKey) {
        long now = nanoClock.getAsLong();
        Bucket bucket = buckets.computeIfAbsent(clientKey, k -> new Bucket(capacity, now));
        bucket.tokens = Math.min(capacity, bucket.tokens + (now - bucket.lastRefillNanos) * tokensPerNano);
        bucket.lastRefillNanos = now;
        if (bucket.tokens >= 1) {
            bucket.tokens -= 1;
            return new Decision(true, (long) Math.floor(bucket.tokens), Duration.ZERO);
        }
        long nanosUntilNextToken = (long) Math.ceil((1 - bucket.tokens) / tokensPerNano);
        return new Decision(false, 0, Duration.ofNanos(nanosUntilNextToken));
    }
}
