package dev.foodfleet.ch01.modulith.kitchen.internal;

import dev.foodfleet.ch01.modulith.order.OrderPlaced;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Creates a kitchen ticket when an order is placed.
 *
 * <p>{@code @ApplicationModuleListener} = {@code @TransactionalEventListener} (runs only after the
 * order transaction commits) + {@code @Async} (the customer doesn't wait for the kitchen) +
 * {@code @Transactional(REQUIRES_NEW)} (the kitchen's write is its own transaction). If this method
 * throws, the publication stays "incomplete" in the registry and is retried, not lost.
 *
 * <p>In remote mode this listener is switched off. kitchen-service consumes
 * {@code foodfleet.order.placed.v1} from Kafka instead (chapter 25).
 */
@Component
@ConditionalOnProperty(name = "foodfleet.kitchen.mode", havingValue = "in-process", matchIfMissing = true)
class OrderPlacedListener {

    private static final Logger log = LoggerFactory.getLogger(OrderPlacedListener.class);

    private final TicketRepository tickets;
    private final Clock clock;

    OrderPlacedListener(TicketRepository tickets, Clock clock) {
        this.tickets = tickets;
        this.clock = clock;
    }

    @ApplicationModuleListener
    void on(OrderPlaced event) {
        boolean created = tickets.createIfAbsent(event.orderId(), event.restaurantId(), clock.instant());
        log.info("kitchen.ticket orderId={} restaurantId={} created={}", event.orderId(), event.restaurantId(), created);
    }
}
