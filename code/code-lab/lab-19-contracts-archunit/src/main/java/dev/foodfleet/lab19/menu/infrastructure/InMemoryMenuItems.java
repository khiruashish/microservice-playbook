package dev.foodfleet.lab19.menu.infrastructure;

import dev.foodfleet.lab19.menu.domain.MenuItem;
import dev.foodfleet.lab19.menu.domain.MenuItems;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

/** Lab 02's JPA repository in a real service; a map here, so the provider test needs no database. */
@Repository
public class InMemoryMenuItems implements MenuItems {

    private final Map<String, MenuItem> items = new ConcurrentHashMap<>();

    public InMemoryMenuItems() {
        save(new MenuItem("7", "paneer-tikka", new BigDecimal("129.50"), true));
    }

    public void save(MenuItem item) {
        items.put(item.restaurantId() + "/" + item.itemId(), item);
    }

    public void clear() {
        items.clear();
    }

    @Override
    public Optional<MenuItem> find(String restaurantId, String itemId) {
        return Optional.ofNullable(items.get(restaurantId + "/" + itemId));
    }
}
