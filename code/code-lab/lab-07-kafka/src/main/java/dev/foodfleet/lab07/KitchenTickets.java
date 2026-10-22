package dev.foodfleet.lab07;

import java.nio.charset.StandardCharsets;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * kitchen-service's consumer. The offset is committed only after this method returns, so a crash means redelivery,
 * never loss; the inbox makes the redelivery harmless. Both writes, the inbox row and the ticket, are one transaction.
 */
@Component
public class KitchenTickets {

    private final JdbcClient db;
    private final JsonMapper json;

    public KitchenTickets(JdbcClient db, JsonMapper json) {
        this.db = db;
        this.json = json;
    }

    @KafkaListener(topics = OrderEvents.TOPIC, groupId = "kitchen-service")
    @Transactional
    public void on(ConsumerRecord<String, String> record) {
        String eventId = header(record, "event-id");
        OrderPlaced event;
        try {
            event = json.readValue(record.value(), OrderPlaced.class);
        } catch (JacksonException e) {
            throw new InvalidEvent("unreadable OrderPlaced at offset " + record.offset(), e);
        }
        int claimed = db.sql("INSERT INTO inbox (event_id) VALUES (?) ON CONFLICT DO NOTHING").param(eventId).update();
        if (claimed == 0) {
            return;                                           // seen before: a redelivery, already applied
        }
        db.sql("INSERT INTO kitchen_ticket (order_id, restaurant_id, item_count) VALUES (?, ?, ?)")
                .params(event.orderId(), event.restaurantId(), event.itemIds().size())
                .update();
    }

    public int ticketsFor(String orderId) {
        return db.sql("SELECT count(*) FROM kitchen_ticket WHERE order_id = ?").param(orderId).query(Integer.class).single();
    }

    private static String header(ConsumerRecord<?, ?> record, String name) {
        Header h = record.headers().lastHeader(name);
        if (h == null) {
            throw new InvalidEvent("no " + name + " header at offset " + record.offset(), null);
        }
        return new String(h.value(), StandardCharsets.UTF_8);
    }
}
