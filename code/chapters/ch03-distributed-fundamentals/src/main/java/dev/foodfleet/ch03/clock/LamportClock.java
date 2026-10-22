package dev.foodfleet.ch03.clock;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Lamport logical clock (Lamport, 1978). Guarantees: if event a happened-before b, then L(a) &lt; L(b).
 * The converse does NOT hold. L(a) &lt; L(b) says nothing about causality, so Lamport timestamps can
 * give a total order but cannot detect concurrency (use {@link VectorClock} for that).
 */
public final class LamportClock {

    private final AtomicLong time = new AtomicLong();

    /** A local event or a send: increment, then use the new value as the event's timestamp. */
    public long tick() {
        return time.incrementAndGet();
    }

    /** On receive: jump past the sender's timestamp, then count the receive as an event. */
    public long onReceive(long remoteTimestamp) {
        return time.updateAndGet(local -> Math.max(local, remoteTimestamp) + 1);
    }

    public long current() {
        return time.get();
    }
}
