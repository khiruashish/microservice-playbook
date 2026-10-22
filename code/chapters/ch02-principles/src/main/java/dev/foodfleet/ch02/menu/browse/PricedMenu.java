package dev.foodfleet.ch02.menu.browse;

import java.util.List;

/**
 * Use-case result: a restaurant's menu with prices for right now. Independent of any API version.
 *
 * @param priceSource name of the pricing.PriceSource used (LIVE, LAST_KNOWN, BASE). Carried as text so
 *                    the API layer never depends on the pricing adapter's types.
 */
public record PricedMenu(
        String restaurantId,
        String restaurantName,
        String zone,
        int multiplierBps,
        String priceSource,
        List<PricedItem> items) {

    public PricedMenu {
        items = List.copyOf(items);
    }

    public record PricedItem(
            String itemId,
            String name,
            boolean veg,
            boolean available,
            long basePricePaise,
            long currentPricePaise) {
    }
}
