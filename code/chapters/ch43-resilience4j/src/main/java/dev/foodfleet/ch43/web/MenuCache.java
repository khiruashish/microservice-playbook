package dev.foodfleet.ch43.web;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** The last menu fetched per restaurant: what order-service shows when restaurant-service can't answer. */
@Component
public class MenuCache {

    private final Map<String, Menu> lastKnown = new ConcurrentHashMap<>();

    public void put(Menu menu) {
        lastKnown.put(menu.restaurantId(), menu);
    }

    public Optional<Menu> get(String restaurantId) {
        return Optional.ofNullable(lastKnown.get(restaurantId));
    }
}
