package dev.foodfleet.ch67.service;

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingStream;

import java.time.Duration;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Garbage collections and their pauses, read from JDK Flight Recorder inside the process: a {@link RecordingStream}
 * with only {@code jdk.GarbageCollection} enabled, so it costs almost nothing. Every collector reports this event
 * with the sum of its stop-the-world pauses and the longest one, which makes collectors comparable.
 */
public final class GcEvents implements AutoCloseable {

    private final RecordingStream stream = new RecordingStream();
    private final AtomicLong collections = new AtomicLong();
    private final AtomicLong pauseNanos = new AtomicLong();
    private final AtomicLong longestPauseNanos = new AtomicLong();
    private final Map<String, Long> byName = new ConcurrentHashMap<>();
    private volatile boolean counting;

    public GcEvents() {
        stream.enable("jdk.GarbageCollection");
        stream.onEvent("jdk.GarbageCollection", this::record);
        // G1 ran out of room while copying live objects: the step before a full, compacting collection
        stream.enable("jdk.EvacuationFailed");
        stream.onEvent("jdk.EvacuationFailed", e -> {
            if (counting) {
                byName.merge("evacuationFailed", 1L, Long::sum);
            }
        });
        stream.startAsync();
    }

    void record(RecordedEvent e) {
        if (!counting) {
            return;
        }
        collections.incrementAndGet();
        byName.merge(e.getString("name"), 1L, Long::sum);
        pauseNanos.addAndGet(e.getDuration("sumOfPauses").toNanos());
        longestPauseNanos.accumulateAndGet(e.getDuration("longestPause").toNanos(), Math::max);
    }

    /** Counts only collections that start from now on. */
    public void startCounting() {
        counting = true;
    }

    public long collections() {
        return collections.get();
    }

    /** Collections by the collector's name for them (G1New, G1Old, G1Full, ZGC Minor, ...), and G1's evacuation failures. */
    public Map<String, Long> byName() {
        return new TreeMap<>(byName);
    }

    public Duration pauses() {
        return Duration.ofNanos(pauseNanos.get());
    }

    public Duration longestPause() {
        return Duration.ofNanos(longestPauseNanos.get());
    }

    @Override
    public void close() {
        stream.close();
    }
}
