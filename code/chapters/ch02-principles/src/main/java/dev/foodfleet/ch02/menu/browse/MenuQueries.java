package dev.foodfleet.ch02.menu.browse;

import dev.foodfleet.ch02.menu.catalog.MenuCatalog;
import dev.foodfleet.ch02.menu.catalog.Restaurant;
import dev.foodfleet.ch02.menu.browse.PricedMenu.PricedItem;
import dev.foodfleet.ch02.menu.pricing.SurgePricing;
import dev.foodfleet.ch02.menu.pricing.SurgeQuote;
import org.springframework.stereotype.Service;

/**
 * The single use case this service exists for. It never fails because of pricing-service:
 * {@link SurgePricing} always returns a quote, degraded if necessary.
 */
@Service
public class MenuQueries {

    private final MenuCatalog catalog;
    private final SurgePricing pricing;

    public MenuQueries(MenuCatalog catalog, SurgePricing pricing) {
        this.catalog = catalog;
        this.pricing = pricing;
    }

    public PricedMenu pricedMenu(String restaurantId) {
        Restaurant restaurant = catalog.findRestaurant(restaurantId)
                .orElseThrow(() -> new RestaurantNotFoundException(restaurantId));
        SurgeQuote quote = pricing.quote(restaurant.zone());

        return new PricedMenu(
                restaurant.restaurantId(),
                restaurant.name(),
                restaurant.zone(),
                quote.multiplierBps(),
                quote.source().name(),
                restaurant.items().stream()
                        .map(item -> new PricedItem(item.itemId(), item.name(), item.veg(), item.available(),
                                item.basePricePaise(), quote.apply(item.basePricePaise())))
                        .toList());
    }
}
