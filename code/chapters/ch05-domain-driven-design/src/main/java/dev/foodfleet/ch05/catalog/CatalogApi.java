package dev.foodfleet.ch05.catalog;

import java.util.List;
import java.util.Optional;

/**
 * The <b>Catalog</b> bounded context's published API, owned by the restaurant-partner team. In
 * production it's an HTTP API of restaurant-service; here it's in-process so the chapter runs
 * without a second service. Its vocabulary (sku, restaurantCode, stockStatus, priceInr as text) is
 * deliberately different from Ordering's, as it usually is between real teams.
 */
public interface CatalogApi {

    Optional<CatalogRestaurant> restaurant(String restaurantCode);

    Optional<CatalogMenuItem> menuItem(String restaurantCode, String sku);

    /**
     * @param stockStatus IN_STOCK, OUT_OF_STOCK or SEASONAL (more values may be added without notice)
     * @param vegFlag     "Y" or "N"
     */
    record CatalogMenuItem(String sku, String restaurantCode, String displayName, String priceInr,
                           String stockStatus, String vegFlag, List<String> allergens, String photoUrl) {
    }

    /** @param status OPEN, CLOSED or PAUSED */
    record CatalogRestaurant(String restaurantCode, String name, String status, String minOrderInr,
                             String packagingChargeInr, String cuisine) {
    }
}
