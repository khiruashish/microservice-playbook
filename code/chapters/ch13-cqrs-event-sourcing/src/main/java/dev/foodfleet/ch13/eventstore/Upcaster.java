package dev.foodfleet.ch13.eventstore;

import java.util.List;

/**
 * Turns an old schema version of an event into the current one, on read. Stored events are never
 * rewritten: history stays exactly as it was recorded.
 */
public interface Upcaster {

    boolean canUpcast(StoredEvent event);

    StoredEvent upcast(StoredEvent event);

    /** Applies upcasters repeatedly until none applies (v1 → v2 → v3 ...). */
    static StoredEvent upcastFully(StoredEvent event, List<Upcaster> upcasters) {
        StoredEvent current = event;
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Upcaster u : upcasters) {
                if (u.canUpcast(current)) {
                    current = u.upcast(current);
                    changed = true;
                }
            }
        }
        return current;
    }
}
