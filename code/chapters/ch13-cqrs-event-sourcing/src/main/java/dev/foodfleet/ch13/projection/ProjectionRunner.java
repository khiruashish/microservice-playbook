package dev.foodfleet.ch13.projection;

import dev.foodfleet.ch13.eventstore.EventStore;
import dev.foodfleet.ch13.eventstore.StoredEvent;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Feeds projections from the global log and remembers, per projection, the last position handled.
 *
 * <p>In a real deployment the read model and its checkpoint are written in <b>one transaction</b>
 * (same database), so a crash can't apply an event twice or skip one. Where that's impossible,
 * handlers must be idempotent (for example by storing the last position per read-model row).
 */
public final class ProjectionRunner {

    private final EventStore store;
    private final int batchSize;
    private final Map<String, Long> checkpoints = new ConcurrentHashMap<>();

    public ProjectionRunner(EventStore store, int batchSize) {
        this.store = store;
        this.batchSize = batchSize;
    }

    /** Catches the projection up with the log; returns how many events it handled. */
    public int catchUp(Projection projection) {
        int handled = 0;
        while (true) {
            long from = checkpoints.getOrDefault(projection.name(), 0L);
            List<StoredEvent> batch = store.readAll(from, batchSize);
            if (batch.isEmpty()) {
                return handled;
            }
            for (StoredEvent e : batch) {
                projection.handle(e);
                checkpoints.put(projection.name(), e.globalPosition());
                handled++;
            }
        }
    }

    /** Throw the read model away and rebuild it from the first event. */
    public int rebuild(Projection projection) {
        projection.reset();
        checkpoints.remove(projection.name());
        return catchUp(projection);
    }

    /** How far behind the log a projection is: the "eventual" in eventual consistency, in events. */
    public long lag(Projection projection) {
        return store.readAll(checkpoints.getOrDefault(projection.name(), 0L), Integer.MAX_VALUE).size();
    }

    public long checkpoint(Projection projection) {
        return checkpoints.getOrDefault(projection.name(), 0L);
    }
}
