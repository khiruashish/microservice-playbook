package dev.foodfleet.ch63;

import dev.foodfleet.ch63.consumer.OrderMenuClient;
import dev.foodfleet.ch63.contract.Contract;
import dev.foodfleet.ch63.contract.StubServer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** order-service's side: its real client against a stub that answers only what its contract says. */
class OrderServiceConsumerTest {

    static final Contract CONTRACT = Contract.load(Path.of("src/test/resources/contracts/order-service-menu-service.json"));

    @Test
    void readsPriceAndAvailability() throws Exception {
        try (StubServer menu = new StubServer(CONTRACT)) {
            OrderMenuClient.Price p = new OrderMenuClient(menu.url()).price(7, "paneer-tikka");
            assertEquals(new BigDecimal("129.50"), p.price());
            assertTrue(p.available());
        }
    }

    @Test
    void anUnknownItemIsUnavailable() throws Exception {
        try (StubServer menu = new StubServer(CONTRACT)) {
            assertFalse(new OrderMenuClient(menu.url()).price(7, "pizza").available());
        }
    }

    @Test
    void aCallTheContractDoesntDescribeFails() throws Exception {
        try (StubServer menu = new StubServer(CONTRACT)) {
            assertThrows(IllegalStateException.class, () -> new OrderMenuClient(menu.url()).price(7, "dal-makhani"));
        }
    }
}
