package dev.foodfleet.ch31.merge;

/**
 * A last-writer-wins register for a single value, such as a delivery note: the write with the greatest
 * timestamp wins on every replica. Safe for values where losing a concurrent write is acceptable, and only with
 * timestamps from a {@link HybridLogicalClock}, so a write made after seeing another always wins over it.
 */
public record LwwRegister<T>(T value, HybridLogicalClock.Timestamp stamp) {

    public LwwRegister<T> merge(LwwRegister<T> other) {
        if (other == null || other.stamp == null) {
            return this;
        }
        if (stamp == null || other.stamp.compareTo(stamp) > 0) {
            return other;
        }
        return this;
    }
}
