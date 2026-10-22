package dev.foodfleet.ch19.consumer;

import dev.foodfleet.ch19.catalog.MenuItem;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * A consumer's local copy of the menu data it needs. Applies an item only if its version is newer
 * than what it has: duplicates and late redeliveries are ignored, so applying is idempotent and
 * order-insensitive. This one check is what makes state-carrying events safe to consume.
 */
public final class Replica {

    private final Map<String, MenuItem> items = new HashMap<>();
    private final Consumer<MenuItem> onApplied;
    private long ignoredStale;

    public Replica(Consumer<MenuItem> onApplied) {
        this.onApplied = onApplied;
    }

    public boolean apply(MenuItem item) {
        MenuItem current = items.get(item.id());
        if (current != null && current.version() >= item.version()) {
            ignoredStale++;
            return false;
        }
        items.put(item.id(), item);
        onApplied.accept(item);
        return true;
    }

    public Optional<MenuItem> get(String itemId) {
        return Optional.ofNullable(items.get(itemId));
    }

    public int size() {
        return items.size();
    }

    public long ignoredStale() {
        return ignoredStale;
    }
}
