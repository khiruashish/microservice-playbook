package dev.foodfleet.ch46.cell;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Shuffle sharding: each tenant gets its own small, pseudo-random subset ({@code shardSize}) of the instances. A
 * poison tenant can take down only its own subset, and another tenant loses everything only if its subset is the
 * same one: with 8 instances and 2 each, that is 1 in 28 tenants, against every tenant in a shared pool.
 */
public final class ShuffleSharder {

    private final List<String> instances;
    private final int shardSize;

    public ShuffleSharder(List<String> instances, int shardSize) {
        if (shardSize < 1 || shardSize > instances.size()) {
            throw new IllegalArgumentException("shard size must be between 1 and " + instances.size());
        }
        this.instances = List.copyOf(instances);
        this.shardSize = shardSize;
    }

    /** The tenant's instances: the {@code shardSize} with the highest hash of tenant + instance. */
    public List<String> shardOf(String tenant) {
        List<String> sorted = new ArrayList<>(instances);
        sorted.sort(Comparator.comparingLong((String i) -> CellRouter.hash(tenant + "#" + i)).reversed());
        return List.copyOf(sorted.subList(0, shardSize));
    }
}
