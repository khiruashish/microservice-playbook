package dev.foodfleet.ch19.app.catalog;

import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * Publishes menu changes to the {@code menuItems-out-0} binding (topic foodfleet.catalog.menu-items).
 * The binder keys each message by item id (see application.yml), so all versions of one item land
 * on one partition, in order, and a compacted topic keeps the latest per item.
 *
 * <p>In catalog-service this is called by the outbox relay (chapter 15), never directly from the
 * transaction that changed the item.
 */
@Component
public class MenuEventPublisher {

    static final String BINDING = "menuItems-out-0";

    private final StreamBridge bridge;

    public MenuEventPublisher(StreamBridge bridge) {
        this.bridge = bridge;
    }

    public void publish(MenuItemUpdated event) {
        boolean sent = bridge.send(BINDING, MessageBuilder.withPayload(event)
                .setHeader("eventType", "MenuItemUpdated")
                .setHeader("eventVersion", 1)
                .build());
        if (!sent) {
            throw new IllegalStateException("menu event not accepted by the binder: " + event.eventId());
        }
    }
}
