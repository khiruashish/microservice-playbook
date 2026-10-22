package dev.foodfleet.lab13;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

/**
 * Menu cards, read through two caches:
 * <ol>
 *   <li><b>L1, in this pod</b>, 5 seconds: no network at all for the hottest restaurants. One load per key at a
 *       time (single flight): concurrent requests for a missing card wait for the first one's load.</li>
 *   <li><b>L2, shared</b> (Redis), 60 seconds ± 10%, so keys written together don't expire together.</li>
 * </ol>
 * If the shared cache is down, cards come from the database: slower, not broken. Plain Java: verify/MenuCardsCheck
 * runs it without Spring or Redis.
 */
public class MenuCards {

    static final Duration L1_TTL = Duration.ofSeconds(5);
    static final Duration L2_TTL = Duration.ofSeconds(60);

    private record Entry(CompletableFuture<String> card, long expiresAtMillis) {
    }

    private final Map<String, Entry> l1 = new ConcurrentHashMap<>();
    private final SharedCache l2;
    private final Function<String, String> database;                 // restaurant id -> menu card JSON
    private final Clock clock;

    public MenuCards(SharedCache l2, Function<String, String> database, Clock clock) {
        this.l2 = l2;
        this.database = database;
        this.clock = clock;
    }

    public String card(String restaurantId) {
        long now = clock.millis();
        boolean[] mine = {false};
        Entry entry = l1.compute(restaurantId, (id, current) -> {
            if (current != null && current.expiresAtMillis() > now) {
                return current;                                       // fresh, or being loaded by someone else
            }
            mine[0] = true;
            return new Entry(new CompletableFuture<>(), now + L1_TTL.toMillis());
        });
        if (mine[0]) {
            try {
                entry.card().complete(load(restaurantId));
            } catch (RuntimeException e) {
                l1.remove(restaurantId, entry);                        // don't cache a failure
                entry.card().completeExceptionally(e);
            }
        }
        return entry.card().join();
    }

    /** After a menu change: this pod's copy now, the shared copy now, other pods' copies within L1_TTL. */
    public void evict(String restaurantId) {
        l1.remove(restaurantId);
        try {
            l2.delete(key(restaurantId));
        } catch (SharedCache.Unavailable e) {
            // the L2 TTL bounds how long the old card can live
        }
    }

    private String load(String restaurantId) {
        try {
            String cached = l2.get(key(restaurantId));
            if (cached != null) {
                return cached;
            }
            String card = database.apply(restaurantId);
            l2.set(key(restaurantId), card, jittered(L2_TTL));
            return card;
        } catch (SharedCache.Unavailable e) {
            return database.apply(restaurantId);
        }
    }

    public static Duration jittered(Duration ttl) {
        long ms = ttl.toMillis();
        return Duration.ofMillis(ms - ms / 10 + ThreadLocalRandom.current().nextLong(ms / 5 + 1));
    }

    static String key(String restaurantId) {
        return "menu-card:" + restaurantId;
    }
}
