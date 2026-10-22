package dev.foodfleet.ch31.merge;

import java.util.function.LongSupplier;

/**
 * A hybrid logical clock (Kulkarni et al., 2014): timestamps close to wall-clock time that never go backwards
 * on a node and are always later than any timestamp the node has received. If region B's clock is 250 ms
 * behind and it changes a note after seeing region A's change, its timestamp still comes after A's, which a
 * plain wall clock doesn't guarantee.
 */
public final class HybridLogicalClock {

    /** Ordered by wall time, then counter, then node id (a deterministic tie-break). */
    public record Timestamp(long wallMillis, int counter, String node) implements Comparable<Timestamp> {

        @Override
        public int compareTo(Timestamp o) {
            int c = Long.compare(wallMillis, o.wallMillis);
            if (c != 0) {
                return c;
            }
            c = Integer.compare(counter, o.counter);
            return c != 0 ? c : node.compareTo(o.node);
        }
    }

    private final String node;
    private final LongSupplier wallClock;
    private long last;
    private int counter;

    public HybridLogicalClock(String node, LongSupplier wallClock) {
        this.node = node;
        this.wallClock = wallClock;
    }

    /** A timestamp for a local event. */
    public synchronized Timestamp now() {
        long wall = wallClock.getAsLong();
        if (wall > last) {
            last = wall;
            counter = 0;
        } else {
            counter++;
        }
        return new Timestamp(last, counter, node);
    }

    /** Takes in a timestamp received from another node; returns the timestamp of the receive event. */
    public synchronized Timestamp observe(Timestamp remote) {
        long wall = wallClock.getAsLong();
        long next = Math.max(wall, Math.max(last, remote.wallMillis()));
        if (next == last && next == remote.wallMillis()) {
            counter = Math.max(counter, remote.counter()) + 1;
        } else if (next == last) {
            counter++;
        } else if (next == remote.wallMillis()) {
            counter = remote.counter() + 1;
        } else {
            counter = 0;
        }
        last = next;
        return new Timestamp(last, counter, node);
    }
}
