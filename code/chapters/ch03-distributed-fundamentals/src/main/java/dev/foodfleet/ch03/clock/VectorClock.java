package dev.foodfleet.ch03.clock;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Immutable vector clock: one counter per node. Unlike Lamport clocks it can tell
 * "a happened before b" apart from "a and b are concurrent", which is what you need to detect
 * conflicting writes instead of silently dropping one.
 */
public record VectorClock(Map<String, Long> entries) {

    public enum Order { BEFORE, AFTER, EQUAL, CONCURRENT }

    public VectorClock {
        entries = Map.copyOf(entries);
        entries.values().forEach(v -> {
            if (v < 0) {
                throw new IllegalArgumentException("counters are non-negative");
            }
        });
    }

    public static VectorClock empty() {
        return new VectorClock(Map.of());
    }

    /** A local event on {@code node}. */
    public VectorClock increment(String node) {
        Map<String, Long> next = new TreeMap<>(entries);
        next.merge(node, 1L, Long::sum);
        return new VectorClock(next);
    }

    /** On receive: component-wise maximum, then count the receive on {@code node}. */
    public VectorClock mergeAndIncrement(VectorClock other, String node) {
        Map<String, Long> next = new TreeMap<>(entries);
        other.entries.forEach((k, v) -> next.merge(k, v, Math::max));
        return new VectorClock(next).increment(node);
    }

    public long get(String node) {
        return entries.getOrDefault(node, 0L);
    }

    public Order compareTo(VectorClock other) {
        boolean anyLess = false;
        boolean anyGreater = false;
        Set<String> nodes = new HashSet<>(entries.keySet());
        nodes.addAll(other.entries.keySet());
        for (String node : nodes) {
            long mine = get(node);
            long theirs = other.get(node);
            if (mine < theirs) {
                anyLess = true;
            } else if (mine > theirs) {
                anyGreater = true;
            }
        }
        if (anyLess && anyGreater) {
            return Order.CONCURRENT;
        }
        if (anyLess) {
            return Order.BEFORE;
        }
        return anyGreater ? Order.AFTER : Order.EQUAL;
    }
}
