package dev.foodfleet.ch19.app.ordering;

import dev.foodfleet.ch19.app.catalog.MenuItemUpdated;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

/**
 * ordering-service's local copy of the menu data it needs to take orders. Updated only by events;
 * read on every order without calling catalog-service. Newer versions win, atomically per item,
 * so duplicates and late redeliveries are harmless. (In production this is a table in ordering's
 * own database, updated with the same version check in SQL.)
 */
@Component
public class MenuReplica {

    public record Item(String itemId, long pricePaise, boolean available, long version) {
    }

    private final ConcurrentMap<String, Item> items = new ConcurrentHashMap<>();

    public boolean apply(MenuItemUpdated event) {
        Item incoming = new Item(event.itemId(), event.pricePaise(), event.available(), event.version());
        Item result = items.merge(event.itemId(), incoming,
                (current, candidate) -> candidate.version() > current.version() ? candidate : current);
        return result == incoming;
    }

    public Optional<Item> get(String itemId) {
        return Optional.ofNullable(items.get(itemId));
    }
}
