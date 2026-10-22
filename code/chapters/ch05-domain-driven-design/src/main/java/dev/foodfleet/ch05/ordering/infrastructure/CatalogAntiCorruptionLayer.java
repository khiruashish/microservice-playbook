package dev.foodfleet.ch05.ordering.infrastructure;

import dev.foodfleet.ch05.catalog.CatalogApi;
import dev.foodfleet.ch05.ordering.domain.Identifiers.MenuItemId;
import dev.foodfleet.ch05.ordering.domain.Identifiers.RestaurantId;
import dev.foodfleet.ch05.ordering.domain.MenuCatalog;
import dev.foodfleet.ch05.ordering.domain.OrderableItem;
import dev.foodfleet.ch05.ordering.domain.RestaurantTerms;
import java.util.Optional;

/**
 * Anti-corruption layer: implements Ordering's {@link MenuCatalog} port using Catalog's API and the
 * {@link CatalogTranslator}. If Catalog is replaced, renamed or split, only this package changes.
 */
public final class CatalogAntiCorruptionLayer implements MenuCatalog {

    private final CatalogApi catalog;
    private final CatalogTranslator translator;

    public CatalogAntiCorruptionLayer(CatalogApi catalog, CatalogTranslator translator) {
        this.catalog = catalog;
        this.translator = translator;
    }

    @Override
    public Optional<OrderableItem> findItem(RestaurantId restaurantId, MenuItemId menuItemId) {
        return catalog.menuItem(restaurantId.value(), menuItemId.value()).map(translator::toOrderableItem);
    }

    @Override
    public Optional<RestaurantTerms> termsFor(RestaurantId restaurantId) {
        return catalog.restaurant(restaurantId.value()).map(translator::toTerms);
    }
}
