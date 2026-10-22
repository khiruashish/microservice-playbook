package dev.foodfleet.lab03;

import java.util.List;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/**
 * menu-service's API as order-service sees it: an interface, implemented at startup by Spring's HTTP service
 * registry. order-service keeps its own copy of the response shape and ignores fields it doesn't use, so menu-service
 * can add fields without breaking it (a tolerant reader, ADR-027).
 */
@HttpExchange("/restaurants/{restaurantId}/menu")
public interface MenuClient {

    record Menu(String restaurantId, List<Item> items) {
    }

    record Item(String id, String name, long pricePaise) {
    }

    @GetExchange
    Menu menu(@PathVariable String restaurantId);
}
