package dev.foodfleet.ch05.ordering.domain;

import dev.foodfleet.ch05.ordering.domain.Identifiers.MenuItemId;
import dev.foodfleet.ch05.ordering.domain.Identifiers.RestaurantId;
import java.util.Objects;

/**
 * Ordering's own view of a dish: only what ordering needs (id, name, price, can-I-order-it).
 * The Catalog context's model has photos, descriptions, allergens, stock codes and more; the
 * anti-corruption layer translates it into this.
 */
public record OrderableItem(MenuItemId id, RestaurantId restaurantId, String name, Money price, boolean available) {

    public OrderableItem {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(restaurantId, "restaurantId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(price, "price");
    }
}
