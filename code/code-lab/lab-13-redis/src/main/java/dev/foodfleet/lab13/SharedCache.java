package dev.foodfleet.lab13;

import java.time.Duration;

/** The second level: a cache shared by every pod. {@link RedisSharedCache} in production; a map in the checks. */
public interface SharedCache {

    /** @return the value, or null on a miss; throws {@link Unavailable} if the cache can't be reached */
    String get(String key);

    void set(String key, String value, Duration ttl);

    void delete(String key);

    final class Unavailable extends RuntimeException {
        public Unavailable(Throwable cause) {
            super(cause);
        }
    }
}
