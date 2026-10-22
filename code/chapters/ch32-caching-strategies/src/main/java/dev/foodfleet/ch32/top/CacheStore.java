package dev.foodfleet.ch32.top;

import java.time.Duration;
import java.util.Optional;

/** The few cache operations {@link SoftTtlCache} needs. */
public interface CacheStore {

    Optional<String> get(String key);

    void put(String key, String value, Duration ttl);

    /** True if the key was absent and is now set (a lock). */
    boolean putIfAbsent(String key, String value, Duration ttl);

    void delete(String key);
}
