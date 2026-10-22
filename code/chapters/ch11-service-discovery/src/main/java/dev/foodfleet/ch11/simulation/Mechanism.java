package dev.foodfleet.ch11.simulation;

import java.util.List;

/**
 * A discovery setup, described as the chain of stages a failure must pass through.
 *
 * @param retriesOnAnotherInstance whether callers retry a failed call on a different instance
 *                                 (then requests to the dead instance are slowed, not failed)
 */
public record Mechanism(String name, List<Stage> stages, boolean retriesOnAnotherInstance) {

    public Mechanism {
        stages = List.copyOf(stages);
    }

    public Mechanism withRetries() {
        return new Mechanism(name + " + retry elsewhere", stages, true);
    }
}
