package dev.foodfleet.ch61;

import dev.foodfleet.ch61.order.HttpMenuClient;
import dev.foodfleet.ch61.order.MenuClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Consumer contract tests: the real HTTP client against menu-service as the shared contract describes it. */
@Tag("contract")
class MenuClientContractTest {

    MenuStub menu;
    MenuClient client;

    @BeforeEach
    void start() throws Exception {
        menu = new MenuStub();
        client = new HttpMenuClient(menu.url());
    }

    @AfterEach
    void stop() {
        menu.close();
    }

    @Test
    void readsThePriceOfAnAvailableItem() {
        MenuClient.Price p = client.price(7, "paneer-tikka");
        assertEquals(new BigDecimal("129.50"), p.price());
        assertTrue(p.available());
    }

    @Test
    void readsThatAnItemIsSoldOut() {
        assertFalse(client.price(7, "kulfi").available());
    }

    @Test
    void treatsAnUnknownItemAsUnavailable() {
        assertFalse(client.price(7, "pizza").available());
    }
}
