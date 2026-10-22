package dev.foodfleet.ch19.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.foodfleet.ch19.app.catalog.MenuEventPublisher;
import dev.foodfleet.ch19.app.catalog.MenuItemUpdated;
import dev.foodfleet.ch19.app.ordering.MenuReplica;
import dev.foodfleet.ch19.app.ordering.OrderPricing;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.EnableTestBinder;
import org.springframework.cloud.stream.binder.test.InputDestination;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

/**
 * The functions and bindings with Spring Cloud Stream's in-memory test binder instead of Kafka:
 * same configuration, same serialization, no broker. Destination names are the topic names.
 */
@SpringBootTest(properties = "spring.cloud.stream.default-binder=test")
@EnableTestBinder
class MenuEventsStreamTest {

    static final String TOPIC = "foodfleet.catalog.menu-items";

    @Autowired InputDestination input;
    @Autowired OutputDestination output;
    @Autowired MenuReplica replica;
    @Autowired OrderPricing pricing;
    @Autowired MenuEventPublisher publisher;

    private static Message<byte[]> json(String itemId, long price, boolean available, long version) {
        String body = """
                {"eventId":"e-%d","itemId":"%s","restaurantId":"rest-1","name":"Paneer tikka","pricePaise":%d,
                 "available":%s,"version":%d,"occurredAt":"2026-10-06T07:30:00Z"}"""
                .formatted(version, itemId, price, available, version);
        return MessageBuilder.withPayload(body.getBytes(StandardCharsets.UTF_8))
                .setHeader("contentType", "application/json").build();
    }

    @Test
    void eventsUpdateTheReplicaAndOrdersArePricedLocally() {
        input.send(json("item-7", 24_900, true, 1), TOPIC);

        assertThat(pricing.linePricePaise("item-7", 2)).isEqualTo(49_800L);
    }

    @Test
    void anOlderVersionArrivingLateIsIgnored() {
        input.send(json("item-8", 26_900, false, 5), TOPIC);       // sold out, version 5
        input.send(json("item-8", 24_900, true, 4), TOPIC);        // a late redelivery of version 4

        assertThat(replica.get("item-8").orElseThrow().version()).isEqualTo(5L);
        assertThatThrownBy(() -> pricing.linePricePaise("item-8", 1)).isInstanceOf(OrderPricing.ItemUnavailable.class);
    }

    @Test
    void thePublisherSendsJsonWithAnEventTypeHeader() {
        publisher.publish(new MenuItemUpdated("e-1", "item-9", "rest-1", "Masala dosa", 14_900, true, 3,
                Instant.parse("2026-10-06T07:30:00Z")));

        Message<byte[]> sent = output.receive(1_000, TOPIC);
        assertThat(new String(sent.getPayload(), StandardCharsets.UTF_8)).contains("\"itemId\":\"item-9\"", "\"version\":3");
        assertThat(sent.getHeaders().get("eventType")).isEqualTo("MenuItemUpdated");
    }
}
