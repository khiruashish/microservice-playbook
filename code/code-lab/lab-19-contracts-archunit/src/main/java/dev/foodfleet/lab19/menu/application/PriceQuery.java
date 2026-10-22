package dev.foodfleet.lab19.menu.application;

import dev.foodfleet.lab19.menu.domain.MenuItem;
import dev.foodfleet.lab19.menu.domain.MenuItems;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class PriceQuery {

    private final MenuItems items;

    public PriceQuery(MenuItems items) {
        this.items = items;
    }

    public Optional<MenuItem> price(String restaurantId, String itemId) {
        return items.find(restaurantId, itemId);
    }
}
