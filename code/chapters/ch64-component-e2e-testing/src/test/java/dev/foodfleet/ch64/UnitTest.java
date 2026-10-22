package dev.foodfleet.ch64;

import com.sun.net.httpserver.HttpServer;
import dev.foodfleet.ch64.app.MenuApp;
import dev.foodfleet.ch64.app.Web;
import dev.foodfleet.ch64.e2e.Journeys;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class UnitTest {

    @Test
    void pollingWaitsUntilTheConditionHolds() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        assertTrue(Journeys.Wait.poll(10, 2_000).until(() -> calls.incrementAndGet() >= 5));
        assertEquals(5, calls.get());
    }

    @Test
    void pollingGivesUpAfterItsTimeout() throws Exception {
        long t = System.nanoTime();
        assertFalse(Journeys.Wait.poll(10, 200).until(() -> false));
        assertTrue((System.nanoTime() - t) / 1_000_000 >= 200);
    }

    @Test
    void aSleepChecksOnce() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        assertFalse(Journeys.Wait.sleep(10).until(() -> calls.incrementAndGet() > 1));
        assertEquals(1, calls.get());
    }

    @Test
    void theDailyDishSellsOut() throws Exception {
        HttpServer menu = MenuApp.start(0, 2);
        try {
            String url = "http://127.0.0.1:" + menu.getAddress().getPort() + "/restaurants/7/items/biryani-of-the-day/reserve";
            assertEquals(200, Web.call("POST", url, null).status());
            assertEquals(200, Web.call("POST", url, null).status());
            assertEquals(409, Web.call("POST", url, null).status());
        } finally {
            menu.stop(0);
        }
    }
}
