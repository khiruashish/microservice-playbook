package dev.foodfleet.ch15.sim;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Seeded fault injection: each named point fails with its own probability, so every run of the
 * simulation is reproducible. {@link #heal()} turns all faults off, to drain the system at the end.
 */
public final class Faults {

    public static final Faults NONE = new Faults(Map.of(), 0);

    private final Map<String, Double> odds;
    private final Map<String, Integer> calls = new HashMap<>();
    private final Map<String, Integer> scripted = new HashMap<>();
    private final Random random;
    private boolean healed;

    public Faults(Map<String, Double> odds, long seed) {
        this.odds = new HashMap<>(odds);
        this.random = new Random(seed);
    }

    /** For tests: the point fails exactly once, on its {@code n}-th check (1-based). */
    public static Faults onceAt(String point, int n) {
        Faults faults = new Faults(Map.of(), 0);
        faults.scripted.put(point, n);
        return faults;
    }

    public boolean fails(String point) {
        if (healed) {
            return false;
        }
        int call = calls.merge(point, 1, Integer::sum);
        if (scripted.containsKey(point)) {
            return scripted.get(point) == call;
        }
        double p = odds.getOrDefault(point, 0.0);
        return p > 0 && random.nextDouble() < p;
    }

    /** Throws a {@link Crash} if the point fails. */
    public void maybeCrash(String point) {
        if (fails(point)) {
            throw new Crash(point);
        }
    }

    public void heal() {
        healed = true;
    }

    public Random random() {
        return random;
    }
}
