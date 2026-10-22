package dev.foodfleet.ch05.ordering.infrastructure;

import dev.foodfleet.ch05.ordering.domain.OrderEvent;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Makes published domain events visible: logs them and keeps the most recent ones in memory for
 * tests and the demo. Downstream contexts (Kitchen, Payment, Notification) would subscribe here.
 */
@Component
public class OrderEventLog {

    private static final Logger log = LoggerFactory.getLogger(OrderEventLog.class);

    private final List<OrderEvent> received = new CopyOnWriteArrayList<>();

    @EventListener
    void on(OrderEvent event) {
        received.add(event);
        log.info("domain.event type={} orderId={}", event.getClass().getSimpleName(), event.orderId().value());
    }

    public List<OrderEvent> received() {
        return List.copyOf(received);
    }
}
