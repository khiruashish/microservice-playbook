package dev.foodfleet.orders.adapter.menu;

import dev.foodfleet.orders.domain.Menu;

import java.util.HashMap;
import java.util.Map;

/** The city's menu items and prices, held in memory. */
public final class InMemoryMenu implements Menu {

    private final Map<String, Long> prices = new HashMap<>();

    public InMemoryMenu(int items) {
        for (int i = 0; i < items; i++) {
            prices.put("item-" + i, 4_900L + (i % 400) * 100L);
        }
    }

    @Override
    public long priceInPaise(String item) {
        Long p = prices.get(item);
        if (p == null) {
            throw new IllegalArgumentException("not on the menu: " + item);
        }
        return p;
    }
}
