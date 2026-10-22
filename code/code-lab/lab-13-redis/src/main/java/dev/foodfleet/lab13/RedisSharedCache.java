package dev.foodfleet.lab13;

import java.time.Duration;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
class RedisSharedCache implements SharedCache {

    private final StringRedisTemplate redis;

    RedisSharedCache(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public String get(String key) {
        try {
            return redis.opsForValue().get(key);
        } catch (DataAccessException e) {
            throw new Unavailable(e);
        }
    }

    @Override
    public void set(String key, String value, Duration ttl) {
        try {
            redis.opsForValue().set(key, value, ttl);
        } catch (DataAccessException e) {
            throw new Unavailable(e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            redis.delete(key);
        } catch (DataAccessException e) {
            throw new Unavailable(e);
        }
    }
}
