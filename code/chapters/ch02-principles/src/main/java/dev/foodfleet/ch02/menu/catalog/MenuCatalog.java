package dev.foodfleet.ch02.menu.catalog;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

/**
 * The menu data this service owns. Nobody else reads it directly; other services ask the API
 * (decentralized data management). In-memory seed data keeps chapter 2 focused on principles.
 * FoodFleet's restaurant-service persists menus in PostgreSQL behind a cache.
 */
@Repository
public class MenuCatalog {

    private final Map<String, Restaurant> restaurants = List.of(
                    new Restaurant("rest-biryani-house", "Biryani House", "BLR-KORAMANGALA", List.of(
                            new MenuItem("dum-biryani", "Hyderabadi Dum Biryani", 34_900, false, true),
                            new MenuItem("veg-biryani", "Veg Dum Biryani", 27_900, true, true),
                            new MenuItem("raita", "Boondi Raita", 6_900, true, true),
                            new MenuItem("double-ka-meetha", "Double ka Meetha", 12_900, true, false))),
                    new Restaurant("rest-dosa-corner", "Dosa Corner", "BLR-INDIRANAGAR", List.of(
                            new MenuItem("masala-dosa", "Masala Dosa", 12_000, true, true),
                            new MenuItem("filter-coffee", "Filter Coffee", 4_500, true, true))))
            .stream()
            .collect(Collectors.toUnmodifiableMap(Restaurant::restaurantId, Function.identity()));

    public Optional<Restaurant> findRestaurant(String restaurantId) {
        return Optional.ofNullable(restaurants.get(restaurantId));
    }
}
