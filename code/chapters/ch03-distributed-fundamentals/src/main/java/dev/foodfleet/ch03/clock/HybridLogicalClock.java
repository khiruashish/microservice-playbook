package dev.foodfleet.ch03.clock;

import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Hybrid Logical Clock (Kulkarni et al., 2014): timestamps that stay close to wall-clock time
 * (useful in logs and for "as of" queries) but never go backwards and always respect causality,
 * even when NTP steps the wall clock back or another node's clock runs ahead.
 * CockroachDB and YugabyteDB use HLCs to version data. The register in this chapter uses one for
 * last-write-wins.
 *
 * <p>Thread-safe: all state transitions are synchronized on this instance.
 */
public final class HybridLogicalClock {

    private final LongSupplier wallClockMillis;
    private long logicalMillis;
    private int counter;

    public HybridLogicalClock(LongSupplier wallClockMillis) {
        this.wallClockMillis = Objects.requireNonNull(wallClockMillis);
    }

    /** Timestamp for a local event or a send. */
    public synchronized Timestamp now() {
        long physical = wallClockMillis.getAsLong();
        if (physical > logicalMillis) {
            logicalMillis = physical;
            counter = 0;
        } else {
            counter++; // wall clock stalled or went backwards: advance the logical part instead
        }
        return new Timestamp(logicalMillis, counter);
    }

    /** Timestamp for receiving a message stamped {@code remote}. Result is greater than both. */
    public synchronized Timestamp onReceive(Timestamp remote) {
        long physical = wallClockMillis.getAsLong();
        long previous = logicalMillis;
        long next = Math.max(Math.max(previous, remote.millis()), physical);
        if (next == previous && next == remote.millis()) {
            counter = Math.max(counter, remote.counter()) + 1;
        } else if (next == previous) {
            counter = counter + 1;
        } else if (next == remote.millis()) {
            counter = remote.counter() + 1;
        } else {
            counter = 0;
        }
        logicalMillis = next;
        return new Timestamp(logicalMillis, counter);
    }

    /** Totally ordered: by millis, then by counter. */
    public record Timestamp(long millis, int counter) implements Comparable<Timestamp> {

        public static final Timestamp ZERO = new Timestamp(0, 0);

        @Override
        public int compareTo(Timestamp other) {
            int byMillis = Long.compare(millis, other.millis);
            return byMillis != 0 ? byMillis : Integer.compare(counter, other.counter);
        }

        public boolean isAfter(Timestamp other) {
            return compareTo(other) > 0;
        }
    }
}
