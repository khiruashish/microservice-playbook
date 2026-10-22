package dev.foodfleet.lab01;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Repository;

/** Menus in memory, for now: lab 02 moves them to PostgreSQL behind the same methods. */
@Repository
public class MenuRepository {

    private final Map<String, List<MenuItem>> menus = new ConcurrentHashMap<>();
    private final AtomicInteger ids = new AtomicInteger(3);

    public MenuRepository() {
        menus.put("r-1", new CopyOnWriteArrayList<>(List.of(
                new MenuItem("m-1", "Paneer tikka", 24900, true),
                new MenuItem("m-2", "Chicken biryani", 32900, false),
                new MenuItem("m-3", "Masala dosa", 14900, true))));
    }

    public Optional<List<MenuItem>> menu(String restaurantId) {
        return Optional.ofNullable(menus.get(restaurantId)).map(List::copyOf);
    }

    public MenuItem add(String restaurantId, NewMenuItem item) {
        List<MenuItem> menu = menus.get(restaurantId);
        if (menu == null) {
            throw new RestaurantNotFoundException(restaurantId);
        }
        MenuItem created = new MenuItem("m-" + ids.incrementAndGet(), item.name(), item.pricePaise(), item.vegetarian());
        menu.add(created);
        return created;
    }
}
