package dev.foodfleet.lab06;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.stereotype.Component;

/**
 * Calls menu-service through three patterns. Spring Boot applies them in a fixed order, whatever the order of the
 * annotations: Retry ( CircuitBreaker ( Bulkhead ( call ) ) ). So every attempt is counted by the breaker, the
 * bulkhead limits concurrent calls, and the fallback, on the outermost @Retry, runs only after retries are spent or
 * a call is refused (chapter 43). On @CircuitBreaker it would answer the first failure and retries would never run.
 */
@Component
public class ResilientMenus {

    public static class MenuUnavailable extends RuntimeException {
        MenuUnavailable(String restaurantId, Throwable cause) {
            super("No menu for " + restaurantId + " and no earlier copy: " + cause.getClass().getSimpleName(), cause);
        }
    }

    private final MenuClient menus;
    private final Map<String, MenuClient.Menu> lastKnown = new ConcurrentHashMap<>();

    public ResilientMenus(MenuClient menus) {
        this.menus = menus;
    }

    @Retry(name = "menu", fallbackMethod = "lastKnownMenu")
    @CircuitBreaker(name = "menu")
    @Bulkhead(name = "menu")
    public MenuClient.Menu menu(String restaurantId) {
        MenuClient.Menu menu = menus.menu(restaurantId);
        lastKnown.put(restaurantId, menu);
        return menu;
    }

    /** The degradation mode (ADR-002): the last menu seen, marked stale, so the app can say "prices may have changed". */
    MenuClient.Menu lastKnownMenu(String restaurantId, Throwable cause) {
        MenuClient.Menu menu = lastKnown.get(restaurantId);
        if (menu == null) {
            throw new MenuUnavailable(restaurantId, cause);
        }
        return menu.asStale();
    }
}
