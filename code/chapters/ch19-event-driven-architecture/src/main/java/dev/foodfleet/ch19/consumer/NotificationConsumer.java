package dev.foodfleet.ch19.consumer;

import dev.foodfleet.ch19.bus.Events.MenuItemChanged;
import dev.foodfleet.ch19.catalog.Catalog;
import dev.foodfleet.ch19.sim.EventLoop;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;

/**
 * Consumes thin events: on "item X changed", call catalog-service for X's current state. Every
 * event costs API calls, and while catalog is down the consumer can't learn anything.
 *
 * <p>Two behaviours:
 * <ul>
 *   <li><b>naive</b>: one fetch per event; a failed fetch is retried every second;</li>
 *   <li><b>careful</b>: at most one fetch in flight per item (events that arrive meanwhile only mark
 *       the item dirty, and one more fetch follows); failures back off exponentially with full
 *       jitter, from 1 s up to 30 s.</li>
 * </ul>
 */
public final class NotificationConsumer implements Consumer<MenuItemChanged> {

    private final EventLoop loop;
    private final Catalog catalog;
    private final Replica replica;
    private final boolean careful;
    private final Random random;
    private final Map<String, Boolean> inFlight = new HashMap<>();   // itemId -> dirty again meanwhile?
    private long retries;

    public NotificationConsumer(EventLoop loop, Catalog catalog, Replica replica, boolean careful, Random random) {
        this.loop = loop;
        this.catalog = catalog;
        this.replica = replica;
        this.careful = careful;
        this.random = random;
    }

    @Override
    public void accept(MenuItemChanged event) {
        String itemId = event.itemId();
        if (!careful) {
            fetch(itemId, 0);
            return;
        }
        if (inFlight.containsKey(itemId)) {
            inFlight.put(itemId, true);                    // coalesce: refetch once when this one ends
            return;
        }
        inFlight.put(itemId, false);
        fetch(itemId, 0);
    }

    private void fetch(String itemId, int failures) {
        catalog.get(itemId, answer -> {
            if (answer.isPresent()) {
                replica.apply(answer.get());
                if (careful) {
                    boolean dirty = inFlight.remove(itemId);
                    if (dirty) {
                        accept(new MenuItemChanged(itemId));
                    }
                }
                return;
            }
            retries++;
            long wait = careful
                    ? (long) (random.nextDouble() * Math.min(EventLoop.seconds(30), EventLoop.seconds(1) << failures))
                    : EventLoop.seconds(1);
            loop.after(wait, () -> fetch(itemId, Math.min(failures + 1, 20)));
        });
    }

    public long retries() {
        return retries;
    }
}
