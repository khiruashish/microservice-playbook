package dev.foodfleet.ch02.menu.catalog;

import java.util.Objects;

/** A dish as the menu catalogue stores it. Prices are in paise. */
public record MenuItem(String itemId, String name, long basePricePaise, boolean veg, boolean available) {

    public MenuItem {
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(name, "name");
        if (basePricePaise < 0) {
            throw new IllegalArgumentException("basePricePaise must be >= 0");
        }
    }
}
