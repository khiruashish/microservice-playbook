package dev.foodfleet.ch13.eventstore;

import java.time.Instant;
import java.util.Map;

/**
 * An event as the store keeps it: schema-agnostic (type + schema version + flat payload), so the
 * store never depends on domain classes and old events can be upcast when the domain changes.
 *
 * @param streamVersion  1-based position within its stream (the aggregate's version)
 * @param globalPosition 1-based position across all streams; projections checkpoint on it
 */
public record StoredEvent(String streamId, long streamVersion, long globalPosition, String type, int schemaVersion,
                          Map<String, String> payload, Instant recordedAt) {

    public StoredEvent {
        payload = Map.copyOf(payload);
    }
}
