package dev.foodfleet.ch15.app.outbox;

/**
 * Sends one message to the broker and returns only when the broker has acknowledged it (for Kafka:
 * {@code kafkaTemplate.send(topic, key, payload).get(timeout)} with {@code acks=all}). Throws if
 * that didn't happen. Chapter 25 plugs in Kafka; this chapter records messages in memory.
 */
public interface MessagePublisher {

    void publish(OutboxMessage message);
}
