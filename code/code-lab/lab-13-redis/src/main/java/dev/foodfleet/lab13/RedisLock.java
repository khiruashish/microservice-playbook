package dev.foodfleet.lab13;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * A lease, not a mutex: it expires, so a holder that pauses longer than the TTL (a GC pause, a slow call) is no
 * longer the holder and doesn't know it. Use it to avoid duplicate work (one pod rebuilds the menu index), and pass
 * {@link Lease#fence()} to the resource you change, which must refuse a token older than one it has seen.
 * verify/redis_lab.py: without fencing, 94-103 of 300 increments were lost under such pauses; with it, none.
 */
@Component
public class RedisLock {

    public record Lease(String key, String token, long fence) {
    }

    private final StringRedisTemplate redis;
    private final RedisScript<Long> unlock;

    public RedisLock(StringRedisTemplate redis, RedisScript<Long> unlock) {
        this.redis = redis;
        this.unlock = unlock;
    }

    public Optional<Lease> tryAcquire(String name, Duration ttl) {
        String key = "lock:" + name;
        String token = UUID.randomUUID().toString();
        Boolean acquired = redis.opsForValue().setIfAbsent(key, token, ttl);          // SET key token NX PX ttl
        if (!Boolean.TRUE.equals(acquired)) {
            return Optional.empty();
        }
        Long fence = redis.opsForValue().increment(key + ":fence");                 // increases with every grant
        return Optional.of(new Lease(key, token, fence == null ? 0 : fence));
    }

    /** Deletes the lock only if it is still ours; never a lock that expired and was granted to someone else. */
    public boolean release(Lease lease) {
        Long deleted = redis.execute(unlock, List.of(lease.key()), lease.token());
        return deleted != null && deleted == 1L;
    }
}
