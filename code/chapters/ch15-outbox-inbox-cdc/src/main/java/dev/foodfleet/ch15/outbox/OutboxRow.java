package dev.foodfleet.ch15.outbox;

import dev.foodfleet.ch15.sim.Message;

/**
 * A row in ordering-service's {@code outbox} table: the event to publish, written in the SAME local
 * transaction as the business change. {@code sequence} gives the publishing order.
 */
public record OutboxRow(long sequence, String messageId, String aggregateId, String type, String payload,
                        boolean published) {

    public static final String TABLE = "outbox";

    public Message toMessage() {
        return new Message(messageId, aggregateId, type, payload);
    }

    public OutboxRow markPublished() {
        return new OutboxRow(sequence, messageId, aggregateId, type, payload, true);
    }

    /** Outbox rows are keyed by sequence, zero-padded so table order is publishing order. */
    public String key() {
        return "%012d".formatted(sequence);
    }
}
