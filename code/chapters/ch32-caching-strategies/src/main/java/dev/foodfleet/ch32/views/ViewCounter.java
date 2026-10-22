package dev.foodfleet.ch32.views;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Counting a view is one HINCRBY in Redis; the database sees one statement a second (ViewFlusher) instead of
 * one per view. Measured: about 12,000 views/s against about 7,000 with an UPDATE per view, with 1 statement/s on
 * the database. The price: a Redis crash without persistence loses the views since the last flush (10,000-
 * 10,700 views, about 0.9 s, in the experiment). Fine for view counts; not for money.
 */
@Component
public class ViewCounter {

    static final String PENDING = "views";

    private final StringRedisTemplate redis;

    public ViewCounter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void viewed(long itemId) {
        redis.opsForHash().increment(PENDING, Long.toString(itemId), 1);
    }
}
