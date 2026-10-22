package dev.foodfleet.ch05.ordering.domain;

import java.util.List;

/** Port for publishing domain events. Infrastructure decides how (Spring events, outbox, Kafka). */
@FunctionalInterface
public interface DomainEventPublisher {

    void publish(List<OrderEvent> events);
}
