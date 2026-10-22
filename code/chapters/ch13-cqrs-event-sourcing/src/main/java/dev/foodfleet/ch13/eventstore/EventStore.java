package dev.foodfleet.ch13.eventstore;

import java.util.List;

/**
 * Append-only, per-stream ordered, globally ordered. The one write operation is
 * {@link #append}: atomic for the batch, and conditional on the stream's current version, which is
 * how two concurrent commands on the same aggregate are detected (optimistic concurrency).
 */
public interface EventStore {

    /** Expected version for a stream that must not exist yet. */
    long NO_STREAM = 0;

    /**
     * Appends {@code events} if the stream is at {@code expectedVersion}.
     *
     * @return the stream's new version
     * @throws ConcurrencyException if another writer appended first
     */
    long append(String streamId, long expectedVersion, List<NewEvent> events);

    /** Events of one stream with version greater than {@code afterVersion}, in order. */
    List<StoredEvent> readStream(String streamId, long afterVersion);

    /** Up to {@code max} events of all streams with global position greater than {@code afterPosition}. */
    List<StoredEvent> readAll(long afterPosition, int max);

    class ConcurrencyException extends RuntimeException {
        public ConcurrencyException(String streamId, long expected, long actual) {
            super("Stream " + streamId + " is at version " + actual + ", expected " + expected);
        }
    }
}
