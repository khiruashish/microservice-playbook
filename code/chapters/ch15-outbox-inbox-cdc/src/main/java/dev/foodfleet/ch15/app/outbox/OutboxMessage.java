package dev.foodfleet.ch15.app.outbox;

import java.time.Instant;
import java.util.UUID;

/** A row of the outbox table, and the message the relay publishes from it. */
public record OutboxMessage(long position, UUID id, String aggregateType, String aggregateId, String type,
                            String payload, Instant createdAt) {

    /** Topic naming convention: one topic per aggregate type. */
    public String topic() {
        return "foodfleet." + aggregateType + ".events";
    }
}
