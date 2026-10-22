package dev.foodfleet.ch05.catalog;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Stand-in for restaurant-service's catalogue, with the upstream team's data exactly as they publish it. */
public final class InMemoryCatalogApi implements CatalogApi {

    private final Map<String, CatalogRestaurant> restaurants = Map.of(
            "rest-biryani-house", new CatalogRestaurant("rest-biryani-house", "Biryani House", "OPEN", "199.00", "10.00", "Hyderabadi"),
            "rest-midnight-dosa", new CatalogRestaurant("rest-midnight-dosa", "Midnight Dosa", "CLOSED", "99.00", "5.00", "South Indian"));

    private final Map<String, CatalogMenuItem> items = Map.of(
            "dum-biryani", new CatalogMenuItem("dum-biryani", "rest-biryani-house", "Hyderabadi Dum Biryani", "349.00", "IN_STOCK", "N", List.of("dairy"), "https://img.example/dum-biryani.jpg"),
            "boondi-raita", new CatalogMenuItem("boondi-raita", "rest-biryani-house", "Boondi Raita", "69.00", "IN_STOCK", "Y", List.of("dairy"), "https://img.example/raita.jpg"),
            "seekh-kebab", new CatalogMenuItem("seekh-kebab", "rest-biryani-house", "Seekh Kebab", "289.00", "OUT_OF_STOCK", "N", List.of(), "https://img.example/kebab.jpg"),
            "mango-lassi", new CatalogMenuItem("mango-lassi", "rest-biryani-house", "Mango Lassi", "129.00", "SEASONAL", "Y", List.of("dairy"), "https://img.example/lassi.jpg"),
            "masala-dosa", new CatalogMenuItem("masala-dosa", "rest-midnight-dosa", "Masala Dosa", "120.00", "IN_STOCK", "Y", List.of(), "https://img.example/dosa.jpg"));

    @Override
    public Optional<CatalogRestaurant> restaurant(String restaurantCode) {
        return Optional.ofNullable(restaurants.get(restaurantCode));
    }

    @Override
    public Optional<CatalogMenuItem> menuItem(String restaurantCode, String sku) {
        return Optional.ofNullable(items.get(sku)).filter(item -> item.restaurantCode().equals(restaurantCode));
    }
}
