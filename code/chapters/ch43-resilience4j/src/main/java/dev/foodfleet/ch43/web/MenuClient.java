package dev.foodfleet.ch43.web;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * order-service's client for restaurant-service menus. The three annotations apply in Spring Boot's default aspect
 * order, whatever their order here: Retry ( CircuitBreaker ( Bulkhead ( call ) ) ). The fallback sits on the
 * outermost one, {@code @Retry}, so it runs only after retries are exhausted or a refusal comes back; on
 * {@code @CircuitBreaker} it would answer every first failure with the cache and the retry would never run.
 */
@Component
public class MenuClient {

    private final RestClient restaurantService;
    private final MenuCache cache;

    public MenuClient(RestClient restaurantService, MenuCache cache) {
        this.restaurantService = restaurantService;
        this.cache = cache;
    }

    @Retry(name = "restaurant-service", fallbackMethod = "lastKnownMenu")
    @CircuitBreaker(name = "restaurant-service")
    @Bulkhead(name = "restaurant-service")
    public Menu menu(String restaurantId) {
        Menu menu = restaurantService.get().uri("/restaurants/{id}/menu", restaurantId).retrieve().body(Menu.class);
        cache.put(menu);
        return menu;
    }

    /** Same parameters plus the failure; Resilience4j finds it by name. */
    Menu lastKnownMenu(String restaurantId, Throwable cause) {
        return cache.get(restaurantId).map(Menu::asStale).orElseThrow(() -> new MenuUnavailableException(restaurantId, cause));
    }
}
