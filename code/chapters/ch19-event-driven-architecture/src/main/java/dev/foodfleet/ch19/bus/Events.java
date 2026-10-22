package dev.foodfleet.ch19.bus;

import dev.foodfleet.ch19.catalog.MenuItem;

/** The two shapes of the same fact, "a menu item changed". */
public final class Events {

    private Events() {
    }

    /** Event notification: something happened to this item; ask me if you want to know what. */
    public record MenuItemChanged(String itemId) {
        public String json() {
            return "{\"type\":\"MenuItemChanged\",\"itemId\":\"" + itemId
                    + "\",\"occurredAt\":\"2026-10-06T07:30:00.000Z\"}";
        }
    }

    /** Event-carried state transfer: here is the item as it now is, with its version. */
    public record MenuItemUpdated(MenuItem item) {
        public String json() {
            return "{\"type\":\"MenuItemUpdated\",\"item\":" + item.json() + "}";
        }
    }
}
