package dev.foodfleet.ch02.menu.api;

import dev.foodfleet.ch02.menu.browse.PricedMenu;
import java.util.List;

/**
 * Wire contracts, one per API version. They are separate from {@link PricedMenu} so the domain can
 * change without breaking clients, and so each version can be removed on its own.
 */
final class MenuResponses {

    private MenuResponses() {
    }

    /**
     * v1: the contract the 2025 mobile app shipped with. Its <em>shape</em> is frozen. Its
     * <em>meaning</em> was kept honest: {@code pricePaise} is what the customer will pay now
     * (surge included), and unavailable items are left out because old apps can't render them.
     */
    record MenuV1(String restaurantId, String restaurantName, List<ItemV1> items) {

        record ItemV1(String itemId, String name, long pricePaise) {
        }

        static MenuV1 from(PricedMenu menu) {
            return new MenuV1(menu.restaurantId(), menu.restaurantName(), menu.items().stream()
                    .filter(PricedMenu.PricedItem::available)
                    .map(i -> new ItemV1(i.itemId(), i.name(), i.currentPricePaise()))
                    .toList());
        }
    }

    /** v2: additive evolution. Veg flag, availability, and a transparent price breakdown. */
    record MenuV2(String restaurantId, String restaurantName, String zone, Pricing pricing, List<ItemV2> items) {

        record Pricing(int multiplierBps, String source) {
        }

        record ItemV2(String itemId, String name, boolean veg, boolean available,
                      long basePricePaise, long currentPricePaise) {
        }

        static MenuV2 from(PricedMenu menu) {
            return new MenuV2(menu.restaurantId(), menu.restaurantName(), menu.zone(),
                    new Pricing(menu.multiplierBps(), menu.priceSource()),
                    menu.items().stream()
                            .map(i -> new ItemV2(i.itemId(), i.name(), i.veg(), i.available(),
                                    i.basePricePaise(), i.currentPricePaise()))
                            .toList());
        }
    }
}
