package dev.foodfleet.lab13;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Per-tenant token buckets in Redis, shared by every menu-service pod: one tenant's burst spends its own tokens,
 * never another tenant's. If Redis can't be reached, each pod falls back to its own bucket per tenant: a bounded
 * limit (pods x the limit, in the worst case), never none and never a refusal of everything (ADR-040).
 */
@Component
public class TenantRateLimiter {

    public record Decision(boolean allowed, long tokensLeft, long retryAfterMillis) {
    }

    public record Limit(int capacity, int perSecond) {
    }

    private final StringRedisTemplate redis;
    @SuppressWarnings("rawtypes")
    private final RedisScript<List> tokenBucket;
    private final Map<String, LocalBucket> fallback = new ConcurrentHashMap<>();

    @SuppressWarnings("rawtypes")
    public TenantRateLimiter(StringRedisTemplate redis, RedisScript<List> tokenBucket) {
        this.redis = redis;
        this.tokenBucket = tokenBucket;
    }

    public Decision tryAcquire(String tenant, String endpoint, Limit limit) {
        String key = "rl:" + tenant + ":" + endpoint;
        try {
            List<?> result = redis.execute(tokenBucket, List.of(key),
                    Integer.toString(limit.capacity()), Integer.toString(limit.perSecond()), "1");
            return new Decision(((Long) result.get(0)) == 1L, (Long) result.get(1), (Long) result.get(2));
        } catch (DataAccessException redisDown) {
            return fallback.computeIfAbsent(key, k -> new LocalBucket(limit)).tryAcquire();
        }
    }

    /** The same algorithm as token-bucket.lua, in this pod's memory. */
    static final class LocalBucket {
        private final Limit limit;
        private double tokens;
        private long at = System.nanoTime();

        LocalBucket(Limit limit) {
            this.limit = limit;
            this.tokens = limit.capacity();
        }

        synchronized Decision tryAcquire() {
            long now = System.nanoTime();
            tokens = Math.min(limit.capacity(), tokens + (now - at) / 1e9 * limit.perSecond());
            at = now;
            if (tokens >= 1) {
                tokens -= 1;
                return new Decision(true, (long) tokens, 0);
            }
            return new Decision(false, 0, (long) Math.ceil((1 - tokens) * 1000 / limit.perSecond()));
        }
    }
}
