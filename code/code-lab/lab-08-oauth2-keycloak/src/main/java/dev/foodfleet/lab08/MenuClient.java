package dev.foodfleet.lab08;

import java.util.List;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange("/restaurants/{restaurantId}/menu")
public interface MenuClient {

    record Menu(String restaurantId, List<Item> items) {
    }

    record Item(String id, String name, long pricePaise) {
    }

    @GetExchange
    Menu menu(@PathVariable String restaurantId);
}
