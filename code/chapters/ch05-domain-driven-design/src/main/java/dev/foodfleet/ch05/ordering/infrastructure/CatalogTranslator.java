package dev.foodfleet.ch05.ordering.infrastructure;

import dev.foodfleet.ch05.catalog.CatalogApi.CatalogMenuItem;
import dev.foodfleet.ch05.catalog.CatalogApi.CatalogRestaurant;
import dev.foodfleet.ch05.ordering.application.UpstreamContractException;
import dev.foodfleet.ch05.ordering.domain.Identifiers.MenuItemId;
import dev.foodfleet.ch05.ordering.domain.Identifiers.RestaurantId;
import dev.foodfleet.ch05.ordering.domain.Money;
import dev.foodfleet.ch05.ordering.domain.OrderableItem;
import dev.foodfleet.ch05.ordering.domain.RestaurantTerms;

/**
 * The translation half of the <b>anti-corruption layer</b>: Catalog's model in, Ordering's model out.
 * Every decision about how to interpret the upstream model is made here, once, and tested:
 * <ul>
 *   <li>{@code priceInr} text ("349.00") becomes {@link Money}; malformed prices are a contract breach.</li>
 *   <li>{@code stockStatus} becomes a boolean. Only IN_STOCK is orderable. SEASONAL and any value
 *       Catalog adds later are treated as unavailable: fail safe, never sell what may not exist.</li>
 *   <li>Fields Ordering doesn't need (allergens, photos, cuisine) are dropped.</li>
 * </ul>
 */
public final class CatalogTranslator {

    public OrderableItem toOrderableItem(CatalogMenuItem item) {
        return new OrderableItem(
                new MenuItemId(item.sku()),
                new RestaurantId(item.restaurantCode()),
                item.displayName(),
                price(item.priceInr(), "priceInr of " + item.sku()),
                "IN_STOCK".equals(item.stockStatus()));
    }

    public RestaurantTerms toTerms(CatalogRestaurant restaurant) {
        return new RestaurantTerms(
                new RestaurantId(restaurant.restaurantCode()),
                "OPEN".equals(restaurant.status()),
                price(restaurant.minOrderInr(), "minOrderInr of " + restaurant.restaurantCode()),
                price(restaurant.packagingChargeInr(), "packagingChargeInr of " + restaurant.restaurantCode()));
    }

    private static Money price(String value, String field) {
        try {
            return Money.ofRupees(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new UpstreamContractException("Catalog sent an invalid " + field + ": '" + value + "'");
        }
    }

}
