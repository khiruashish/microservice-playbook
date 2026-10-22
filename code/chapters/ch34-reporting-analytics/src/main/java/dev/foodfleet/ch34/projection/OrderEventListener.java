package dev.foodfleet.ch34.projection;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Order events from the order service's outbox (chapter 15), keyed by order id. Redeliveries after a crash or a
 * rebalance are harmless: the projector ignores any version it has already applied.
 */
@Component
public class OrderEventListener {

    private final OrderFactProjector projector;

    public OrderEventListener(OrderFactProjector projector) {
        this.projector = projector;
    }

    @KafkaListener(id = "reporting-order-facts", topics = "order-events", groupId = "reporting")
    public void onEvent(OrderEvent event) {
        projector.apply(event);
    }
}
