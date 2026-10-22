package dev.foodfleet.ch19.catalog;

import dev.foodfleet.ch19.sim.EventLoop;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.Consumer;

/**
 * catalog-service: owns menu items (the truth), changes them through the lunch rush (items sell out
 * and come back, prices change), and serves {@code GET /items/{id}} (10–30 ms). During its outage
 * window the API fails; changes made just before are still published (they were committed with an
 * outbox, chapter 15).
 */
public final class Catalog {

    public interface ChangeListener {
        void changed(MenuItem item, long at);
    }

    private final EventLoop loop;
    private final Random random;
    private final List<MenuItem> items = new ArrayList<>();
    private final long outageStart;
    private final long outageEnd;
    private long apiCalls;
    private long apiFailures;

    public Catalog(EventLoop loop, Random random, int itemCount, long outageStart, long outageEnd) {
        this.loop = loop;
        this.random = random;
        this.outageStart = outageStart;
        this.outageEnd = outageEnd;
        for (int i = 0; i < itemCount; i++) {
            items.add(new MenuItem("item-" + i, "rest-" + i / 25, "Dish number " + i,
                    9_900 + 50L * random.nextInt(800), true, 1));
        }
    }

    public int size() {
        return items.size();
    }

    public MenuItem truth(int index) {
        return items.get(index);
    }

    public List<MenuItem> all() {
        return List.copyOf(items);
    }

    public static int index(String itemId) {
        return Integer.parseInt(itemId.substring("item-".length()));
    }

    /** One change to a random item: 70% availability flips (sold out / back), 30% price changes. */
    public MenuItem change() {
        int i = random.nextInt(items.size());
        MenuItem current = items.get(i);
        MenuItem next = random.nextDouble() < 0.7
                ? current.withAvailable(!current.available())
                : current.withPrice(Math.max(4_900, current.pricePaise() + 500L * (random.nextInt(9) - 4)));
        items.set(i, next);
        return next;
    }

    public boolean down() {
        return loop.now() >= outageStart && loop.now() < outageEnd;
    }

    /** GET /items/{id}: answers with the CURRENT state after 10–30 ms, or fails during the outage. */
    public void get(String itemId, Consumer<Optional<MenuItem>> reply) {
        apiCalls++;
        long latency = EventLoop.ms(10 + 20 * random.nextDouble());
        if (down()) {
            apiFailures++;
            loop.after(EventLoop.ms(5), () -> reply.accept(Optional.empty()));
            return;
        }
        loop.after(latency, () -> reply.accept(Optional.of(items.get(index(itemId)))));
    }

    public long apiCalls() {
        return apiCalls;
    }

    public long apiFailures() {
        return apiFailures;
    }
}
