package dev.foodfleet.ch43.web;

import java.time.Instant;
import java.util.List;

/** A restaurant's menu as order-service sees it; {@code stale} marks a cached copy served as a fallback. */
public record Menu(String restaurantId, List<String> items, Instant fetchedAt, boolean stale) {

    public Menu asStale() {
        return new Menu(restaurantId, items, fetchedAt, true);
    }
}
