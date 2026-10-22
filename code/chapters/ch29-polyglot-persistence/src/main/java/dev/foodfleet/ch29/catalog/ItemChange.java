package dev.foodfleet.ch29.catalog;

/** A change to one menu item's price or availability, as the restaurant app sends it. */
public record ItemChange(int pricePaise, boolean available) {

    public ItemChange {
        if (pricePaise <= 0) {
            throw new IllegalArgumentException("pricePaise must be positive");
        }
    }
}
