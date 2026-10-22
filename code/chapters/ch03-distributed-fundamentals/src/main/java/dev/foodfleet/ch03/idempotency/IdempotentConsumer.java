package dev.foodfleet.ch03.idempotency;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * "Exactly-once" processing, honestly named: <b>at-least-once delivery + deduplication = exactly-once
 * effects</b>. The broker or network may deliver a message twice. This consumer remembers the ids it
 * has processed and applies each message's effect once.
 *
 * <p>The dedup window is bounded (LRU). Size it to cover the longest redelivery delay you expect.
 * In production the processed-id record must be written <em>in the same transaction</em> as the
 * effect (an inbox table, chapter 15). Otherwise a crash between "apply effect" and "remember id"
 * re-applies the effect on redelivery. This in-memory version shows the logic, not the durability.
 */
public final class IdempotentConsumer<T> {

    private final Consumer<T> effect;
    private final Map<String, Boolean> processed;

    public IdempotentConsumer(int dedupWindow, Consumer<T> effect) {
        if (dedupWindow < 1) {
            throw new IllegalArgumentException("dedupWindow must be >= 1");
        }
        this.effect = Objects.requireNonNull(effect);
        this.processed = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                return size() > dedupWindow;
            }
        };
    }

    /** @return true if the effect was applied, false if the message was a duplicate */
    public synchronized boolean handle(String messageId, T payload) {
        if (processed.containsKey(messageId)) {
            return false;
        }
        effect.accept(payload);
        processed.put(messageId, Boolean.TRUE);
        return true;
    }
}
