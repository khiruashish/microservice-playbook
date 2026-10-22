package dev.foodfleet.lab12;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * kitchen-service's side. With listener observation on, the container reads the record's traceparent header and runs
 * this method in a span whose parent is order-service's send: one trace from the HTTP request to the ticket.
 */
@Component
class KitchenListener {

    private static final Logger log = LoggerFactory.getLogger(KitchenListener.class);

    @KafkaListener(topics = PlaceOrder.TOPIC, groupId = "kitchen-service")
    void onOrderPlaced(String payload) {
        log.info("ticket created");
    }
}
