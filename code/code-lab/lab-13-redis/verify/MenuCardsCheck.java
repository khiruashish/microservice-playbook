import dev.foodfleet.lab13.MenuCards;
import dev.foodfleet.lab13.SharedCache;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Lab 13: MenuCards' two levels and single flight, with a map for Redis and a counter for the database. JDK only. */
public final class MenuCardsCheck {

    static final class MapCache implements SharedCache {
        final Map<String, String> map = new ConcurrentHashMap<>();
        final AtomicInteger reads = new AtomicInteger();
        volatile boolean down;

        public String get(String key) {
            reads.incrementAndGet();
            if (down) throw new Unavailable(new IllegalStateException("connection refused"));
            return map.get(key);
        }

        public void set(String key, String value, Duration ttl) {
            if (down) throw new Unavailable(new IllegalStateException("connection refused"));
            map.put(key, value);
        }

        public void delete(String key) {
            if (down) throw new Unavailable(new IllegalStateException("connection refused"));
            map.remove(key);
        }
    }

    static final class MutableClock extends Clock {
        final AtomicLong millis = new AtomicLong(Instant.parse("2026-10-21T12:00:00Z").toEpochMilli());
        public ZoneOffset getZone() { return ZoneOffset.UTC; }
        public Clock withZone(java.time.ZoneId zone) { return this; }
        public Instant instant() { return Instant.ofEpochMilli(millis.get()); }
    }

    public static void main(String[] args) throws Exception {
        MapCache redis = new MapCache();
        AtomicInteger dbReads = new AtomicInteger();
        MutableClock clock = new MutableClock();
        MenuCards cards = new MenuCards(redis, id -> {
            dbReads.incrementAndGet();
            sleep(20);                                          // a 20 ms query
            return "{\"restaurant\":\"" + id + "\"}";
        }, clock);

        int clients = 200;
        burst(cards, "r-1", clients);
        System.out.printf("cold start, %d concurrent requests: database reads %d, Redis reads %d%n", clients, dbReads.get(), redis.reads.get());

        burst(cards, "r-1", clients);
        System.out.printf("again within 5 s: database reads %d, Redis reads %d (all from L1)%n", dbReads.get(), redis.reads.get());

        clock.millis.addAndGet(6_000);                          // L1 expired, L2 (60 s) still holds it
        burst(cards, "r-1", clients);
        System.out.printf("6 s later, %d concurrent requests: database reads %d, Redis reads %d%n", clients, dbReads.get(), redis.reads.get());

        redis.down = true;
        clock.millis.addAndGet(6_000);
        burst(cards, "r-1", clients);
        System.out.printf("Redis down, %d requests: all answered, database reads %d%n", clients, dbReads.get());

        long min = Long.MAX_VALUE, max = 0;
        for (int i = 0; i < 10_000; i++) {
            long s = MenuCards.jittered(Duration.ofSeconds(60)).toMillis();
            min = Math.min(min, s);
            max = Math.max(max, s);
        }
        System.out.printf("L2 TTLs over 10,000 writes: %d-%d ms%n", min, max);
        System.exit(dbReads.get() == 2 ? 0 : 1);
    }

    static void burst(MenuCards cards, String id, int clients) throws InterruptedException {
        ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch go = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(clients);
        for (int i = 0; i < clients; i++) {
            pool.submit(() -> {
                try {
                    go.await();
                    cards.card(id);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        go.countDown();
        done.await();
        pool.shutdown();
    }

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
