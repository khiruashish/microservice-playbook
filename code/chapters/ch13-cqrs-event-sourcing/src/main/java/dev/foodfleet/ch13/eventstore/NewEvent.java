package dev.foodfleet.ch13.eventstore;

import java.util.Map;

/** An event to append; the store assigns versions, position and time. */
public record NewEvent(String type, int schemaVersion, Map<String, String> payload) {

    public NewEvent {
        payload = Map.copyOf(payload);
    }
}
