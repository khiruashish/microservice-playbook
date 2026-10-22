package dev.foodfleet.ch05.ordering.domain;

import dev.foodfleet.ch05.ordering.domain.Identifiers.MenuItemId;
import dev.foodfleet.ch05.ordering.domain.Identifiers.RestaurantId;
import java.util.Optional;

/**
 * Port to the Catalog context, expressed in <em>Ordering's</em> language. The implementation is an
 * anti-corruption layer that translates Catalog's model, so Catalog's names, types and quirks never
 * leak into this package.
 */
public interface MenuCatalog {

    Optional<OrderableItem> findItem(RestaurantId restaurantId, MenuItemId menuItemId);

    Optional<RestaurantTerms> termsFor(RestaurantId restaurantId);
}
