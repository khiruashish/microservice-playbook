package dev.foodfleet.lab07;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * order-service's side: events keyed by order id, so one order's events stay in order on one partition, with an
 * event id header for deduplication (ADR-025, ADR-028). Sending straight from the business code like this is a dual
 * write: if the database commit and the send don't both happen, events are lost or invented. Lab 09 fixes that.
 */
@Component
public class OrderEvents {

    public static final String TOPIC = "foodfleet.ordering.order-events";

    private final KafkaTemplate<String, String> kafka;
    private final JsonMapper json;

    public OrderEvents(KafkaTemplate<String, String> kafka, JsonMapper json) {
        this.kafka = kafka;
        this.json = json;
    }

    public CompletableFuture<SendResult<String, String>> placed(OrderPlaced event) {
        return send(UUID.randomUUID().toString(), event.orderId(), json.writeValueAsString(event));
    }

    /** Visible for the tests: send a given event id (a redelivery) or a raw payload (a poison message). */
    CompletableFuture<SendResult<String, String>> send(String eventId, String orderId, String payload) {
        ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC, orderId, payload);
        record.headers().add("event-id", eventId.getBytes(StandardCharsets.UTF_8));
        record.headers().add("event-type", "OrderPlaced".getBytes(StandardCharsets.UTF_8));
        return kafka.send(record);
    }
}
