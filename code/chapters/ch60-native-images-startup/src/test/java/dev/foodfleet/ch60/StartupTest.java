package dev.foodfleet.ch60;

import dev.foodfleet.ch60.menu.MenuCatalog;
import dev.foodfleet.ch60.startup.Warmup;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StartupTest {

    @Test
    void aRestaurantsMenuIsTheSameEveryTime() {
        assertEquals(MenuCatalog.menu(7, 150), MenuCatalog.menu(7, 150));
        assertNotEquals(MenuCatalog.menu(7, 150), MenuCatalog.menu(8, 150));
        assertEquals(150, MenuCatalog.menu(7, 150).size());
    }

    @Test
    void discountsRoundHalfEvenToPaise() {
        assertEquals(new BigDecimal("116.55"), MenuCatalog.discounted(new BigDecimal("129.50"), 10));
        assertEquals(new BigDecimal("0.12"), MenuCatalog.discounted(new BigDecimal("0.125"), 0));
        assertEquals(new BigDecimal("0.00"), MenuCatalog.discounted(new BigDecimal("45.00"), 100));
    }

    @Test
    void renderGroupsEveryItemByCategory() {
        String json = MenuCatalog.render(MenuCatalog.menu(7, 150), 10);
        assertTrue(json.startsWith("{\"categories\":[{\"name\":\"breads\""), json.substring(0, 60));
        assertEquals(150, json.split("\"id\":").length - 1);
        assertTrue(json.endsWith("]}]}"));
    }

    @Test
    void warmupStopsAtTheIterationCount() {
        AtomicInteger calls = new AtomicInteger();
        Warmup.Result r = Warmup.run(i -> calls.incrementAndGet(), 500, 60_000);
        assertEquals(500, r.iterations());
        assertEquals(500, calls.get());
    }

    @Test
    void warmupStopsAtTheTimeBudget() {
        Warmup.Result r = Warmup.run(i -> {
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, 1_000_000, 100);
        assertTrue(r.iterations() < 1_000_000);
        assertTrue(r.millis() >= 100 && r.millis() < 1_000, r.toString());
    }
}
