package dev.foodfleet.ch25.kafka;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Which event ids this consumer has already applied. In production this is the inbox table of
 * chapter 15, written in the same transaction as the effect; here it is in memory.
 */
@Component
public class ProcessedEvents {

    private final Set<String> seen = ConcurrentHashMap.newKeySet();

    /** True the first time an id is seen. */
    public boolean firstTime(String eventId) {
        return seen.add(eventId);
    }

    /** Undo {@link #firstTime} when the effect did not happen (here; a rolled-back transaction in production). */
    public void forget(String eventId) {
        seen.remove(eventId);
    }
}
