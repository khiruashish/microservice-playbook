package dev.foodfleet.ch29.projection;

import java.util.List;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Redis copy of item prices, read by cart pricing. Written only by {@link PriceProjector}, and only through
 * {@link #APPLY_IF_NEWER}: a change is applied if its version is newer than the one stored, so replays,
 * duplicates and out-of-order deliveries cannot move the copy backwards.
 */
@Component
public class PriceCache {

    public record CachedPrice(int version, int pricePaise, boolean available) {
    }

    static final RedisScript<Long> APPLY_IF_NEWER = RedisScript.of("""
            local current = redis.call('HGET', KEYS[1], 'v')
            if current and tonumber(current) >= tonumber(ARGV[1]) then return 0 end
            redis.call('HSET', KEYS[1], 'v', ARGV[1], 'p', ARGV[2], 'a', ARGV[3])
            return 1""", Long.class);

    private final StringRedisTemplate redis;

    public PriceCache(StringRedisTemplate redis) {
        this.redis = redis;
    }

    static String key(long itemId) {
        return "price:" + itemId;
    }

    /** True if applied, false if the stored version was already as new. Throws if Redis is unreachable. */
    public boolean apply(long itemId, int version, int pricePaise, boolean available) {
        Long applied = redis.execute(APPLY_IF_NEWER, List.of(key(itemId)),
                Integer.toString(version), Integer.toString(pricePaise), available ? "1" : "0");
        return applied != null && applied == 1L;
    }

    public Optional<CachedPrice> find(long itemId) {
        List<Object> fields = redis.opsForHash().multiGet(key(itemId), List.of("v", "p", "a"));
        if (fields == null || fields.getFirst() == null) {
            return Optional.empty();
        }
        return Optional.of(new CachedPrice(Integer.parseInt((String) fields.get(0)), Integer.parseInt((String) fields.get(1)),
                "1".equals(fields.get(2))));
    }
}
