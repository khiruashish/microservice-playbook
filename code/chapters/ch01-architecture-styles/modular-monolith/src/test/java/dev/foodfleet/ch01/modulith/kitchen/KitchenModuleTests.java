package dev.foodfleet.ch01.modulith.kitchen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.foodfleet.ch01.modulith.order.OrderPlaced;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.Scenario;
import org.springframework.test.context.TestPropertySource;

/**
 * The kitchen module tested on its own: it is driven purely by OrderPlaced events, so the test
 * publishes events instead of calling the order module. Restaurant ids are unique per test
 * because the in-memory database is shared across cached test contexts.
 */
@ApplicationModuleTest
@TestPropertySource(properties = "foodfleet.kitchen.max-active-tickets=2")
class KitchenModuleTests {

    private final KitchenOperations kitchen;

    KitchenModuleTests(@Autowired KitchenOperations kitchen) {
        this.kitchen = kitchen;
    }

    @Test
    void createsTicketWhenOrderIsPlaced(Scenario scenario) {
        UUID orderId = UUID.randomUUID();

        scenario.publish(orderPlaced(orderId, "rest-k-1"))
                .andWaitForStateChange(() -> kitchen.ticketFor(orderId))
                .andVerify(ticket -> assertThat(ticket)
                        .hasValueSatisfying(t -> assertThat(t.status()).isEqualTo("QUEUED")));
    }

    @Test
    void duplicateDeliveryCreatesOneTicket_idempotentConsumer(Scenario scenario) {
        UUID orderId = UUID.randomUUID();
        OrderPlaced event = orderPlaced(orderId, "rest-k-2");

        scenario.publish(event).andWaitForStateChange(() -> kitchen.ticketFor(orderId)).andVerify(t -> { });
        scenario.publish(event).andWaitForStateChange(() -> kitchen.ticketFor(orderId)).andVerify(t -> { });

        // The redelivery is processed asynchronously; the count must stay at exactly one.
        await().during(Duration.ofMillis(500)).atMost(Duration.ofSeconds(3))
                .until(() -> kitchen.availability("rest-k-2").activeTickets() == 1);
    }

    @Test
    void stopsAcceptingAtCapacity_backPressure(Scenario scenario) {
        String restaurant = "rest-k-3";
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        scenario.publish(orderPlaced(first, restaurant)).andWaitForStateChange(() -> kitchen.ticketFor(first)).andVerify(t -> { });
        scenario.publish(orderPlaced(second, restaurant)).andWaitForStateChange(() -> kitchen.ticketFor(second)).andVerify(t -> { });

        assertThat(kitchen.availability(restaurant).accepting()).isFalse();
    }

    @Test
    void pausedKitchenRejects() {
        kitchen.pause("rest-k-4");
        assertThat(kitchen.availability("rest-k-4").accepting()).isFalse();

        kitchen.resume("rest-k-4");
        assertThat(kitchen.availability("rest-k-4").accepting()).isTrue();
    }

    private static OrderPlaced orderPlaced(UUID orderId, String restaurantId) {
        return new OrderPlaced(orderId, "cust-7", restaurantId, 25_000, 1, Instant.now());
    }
}
