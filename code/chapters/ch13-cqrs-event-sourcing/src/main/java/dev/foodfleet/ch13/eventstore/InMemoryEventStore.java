package dev.foodfleet.ch13.eventstore;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Thread-safe in-memory store; passes the same contract test as the JDBC one. */
public final class InMemoryEventStore implements EventStore {

    private final List<StoredEvent> all = new ArrayList<>();
    private final Map<String, List<StoredEvent>> streams = new HashMap<>();
    private final Clock clock;

    public InMemoryEventStore(Clock clock) {
        this.clock = clock;
    }

    @Override
    public synchronized long append(String streamId, long expectedVersion, List<NewEvent> events) {
        List<StoredEvent> stream = streams.computeIfAbsent(streamId, s -> new ArrayList<>());
        long current = stream.size();
        if (current != expectedVersion) {
            throw new ConcurrencyException(streamId, expectedVersion, current);
        }
        for (NewEvent e : events) {
            StoredEvent stored = new StoredEvent(streamId, stream.size() + 1, all.size() + 1, e.type(), e.schemaVersion(),
                    e.payload(), clock.instant());
            stream.add(stored);
            all.add(stored);
        }
        return stream.size();
    }

    @Override
    public synchronized List<StoredEvent> readStream(String streamId, long afterVersion) {
        List<StoredEvent> stream = streams.getOrDefault(streamId, List.of());
        return List.copyOf(stream.subList((int) Math.min(afterVersion, stream.size()), stream.size()));
    }

    @Override
    public synchronized List<StoredEvent> readAll(long afterPosition, int max) {
        int from = (int) Math.min(afterPosition, all.size());
        return List.copyOf(all.subList(from, (int) Math.min(all.size(), (long) from + max)));
    }
}
