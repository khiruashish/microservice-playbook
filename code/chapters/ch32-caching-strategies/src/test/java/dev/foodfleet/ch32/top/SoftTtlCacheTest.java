package dev.foodfleet.ch32.top;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class SoftTtlCacheTest {

    /** A map with expiry, driven by the test's clock. */
    static final class FakeStore implements CacheStore {
        final Map<String, String> values = new HashMap<>();
        final Map<String, Long> expiry = new HashMap<>();
        final MutableClock clock;

        FakeStore(MutableClock clock) {
            this.clock = clock;
        }

        @Override
        public Optional<String> get(String key) {
            Long until = expiry.get(key);
            if (until != null && clock.millis() >= until) {
                values.remove(key);
                expiry.remove(key);
            }
            return Optional.ofNullable(values.get(key));
        }

        @Override
        public void put(String key, String value, Duration ttl) {
            values.put(key, value);
            expiry.put(key, clock.millis() + ttl.toMillis());
        }

        @Override
        public boolean putIfAbsent(String key, String value, Duration ttl) {
            if (get(key).isPresent()) {
                return false;
            }
            put(key, value, ttl);
            return true;
        }

        @Override
        public void delete(String key) {
            values.remove(key);
            expiry.remove(key);
        }
    }

    static final class MutableClock extends Clock {
        long millis = 1_000_000;

        @Override
        public long millis() {
            return millis;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }

    final MutableClock clock = new MutableClock();
    final FakeStore store = new FakeStore(clock);
    final List<Runnable> background = new ArrayList<>();
    final SoftTtlCache cache = new SoftTtlCache(store, clock, background::add, Duration.ofSeconds(30), Duration.ofHours(1),
            Duration.ofSeconds(10));
    final AtomicInteger loads = new AtomicInteger();

    Supplier<String> loader(String value) {
        return () -> {
            loads.incrementAndGet();
            return value;
        };
    }

    @Test
    void aColdMissLoadsOnceAndThenServesFromTheCache() {
        assertThat(cache.get("top", loader("v1"))).isEqualTo("v1");
        assertThat(cache.get("top", loader("v2"))).isEqualTo("v1");
        assertThat(loads.get()).isEqualTo(1);
        assertThat(store.get("refresh:top")).isEmpty();                 // the cold-load lock was released
    }

    @Test
    void afterTheSoftExpiryTheStaleValueIsServedAndOneCallerRefreshesInTheBackground() {
        cache.get("top", loader("v1"));
        clock.millis += 31_000;

        for (int i = 0; i < 50; i++) {                                    // 50 requests arrive after expiry
            assertThat(cache.get("top", loader("v2"))).isEqualTo("v1");    // nobody waits
        }
        assertThat(background).hasSize(1);                               // and only one refresh is started

        background.getFirst().run();
        assertThat(cache.get("top", loader("v3"))).isEqualTo("v2");
        assertThat(loads.get()).isEqualTo(2);
    }

    @Test
    void aFailingRefreshKeepsServingTheOldValueAndRetriesLater() {
        cache.get("top", loader("v1"));
        clock.millis += 31_000;
        cache.get("top", () -> {
            throw new IllegalStateException("database down");
        });
        background.getFirst().run();                                      // fails, logs, releases the lock

        assertThat(cache.get("top", loader("v2"))).isEqualTo("v1");
        assertThat(background).hasSize(2);                                // the next request tries again
    }

    @Test
    void afterTheHardTtlTheValueIsGoneAndLoadedAgain() {
        cache.get("top", loader("v1"));
        clock.millis += Duration.ofHours(1).toMillis() + 1;
        assertThat(cache.get("top", loader("v2"))).isEqualTo("v2");
    }

    @Test
    void valuesMayContainTheSeparator() {
        SoftTtlCache.Entry e = new SoftTtlCache.Entry(42, "a|b|c");
        assertThat(SoftTtlCache.Entry.decode(e.encode())).isEqualTo(e);
    }
}
