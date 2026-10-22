package dev.foodfleet.lab06;

import java.util.List;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/** menu-service's API, as in lab 03. */
@HttpExchange("/restaurants/{restaurantId}/menu")
public interface MenuClient {

    record Menu(String restaurantId, List<Item> items, boolean stale) {

        Menu asStale() {
            return new Menu(restaurantId, items, true);
        }
    }

    record Item(String id, String name, long pricePaise) {
    }

    @GetExchange
    Menu menu(@PathVariable String restaurantId);
}
