package dev.foodfleet.ch02.menu.catalog;

import java.util.List;
import java.util.Objects;

/**
 * @param zone delivery zone (for example {@code BLR-KORAMANGALA}). Surge pricing is set per zone.
 */
public record Restaurant(String restaurantId, String name, String zone, List<MenuItem> items) {

    public Restaurant {
        Objects.requireNonNull(restaurantId, "restaurantId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(zone, "zone");
        items = List.copyOf(items);
    }
}
