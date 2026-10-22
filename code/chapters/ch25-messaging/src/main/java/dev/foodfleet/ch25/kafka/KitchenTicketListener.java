package dev.foodfleet.ch25.kafka;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Keeps restaurant tablets in step with orders. Order matters (ACCEPTED must not arrive before
 * PLACED), so failures are retried in place by the container's error handler (blocking the
 * partition, see {@link KafkaErrorHandlingConfig}); bad events go straight to the DLT.
 */
@Component
public class KitchenTicketListener {

    private final KitchenDisplay kitchen;
    private final ProcessedEvents processed;

    public KitchenTicketListener(KitchenDisplay kitchen, ProcessedEvents processed) {
        this.kitchen = kitchen;
        this.processed = processed;
    }

    @KafkaListener(id = "kitchen-tickets", topics = "order-events", groupId = "kitchen-tickets")
    public void onEvent(OrderEvent event) {
        if (event.orderId() == null || event.restaurantId() == null || event.type() == null) {
            throw new InvalidEventException("event " + event.eventId() + " has no order, restaurant or type");
        }
        if (!processed.firstTime(event.eventId())) {
            return;                                   // redelivered after a crash or rebalance: already applied
        }
        try {
            kitchen.show(event);                      // may throw KitchenDisplay.Unavailable -> retried
        } catch (RuntimeException e) {
            processed.forget(event.eventId());        // not applied: the retry must not be skipped as a duplicate
            throw e;
        }
    }
}
