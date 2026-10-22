package dev.foodfleet.ch14.simulation;

import dev.foodfleet.ch14.placeorder.Faults;
import java.util.Map;
import java.util.SplittableRandom;

/** Seeded fault injection: per "participant.operation", probabilities of reject, transient and lost reply. */
public final class RandomFaults implements Faults {

    public record Odds(double reject, double transientFailure, double lostReply) {
    }

    private final Map<String, Odds> odds;
    private final SplittableRandom random;
    private boolean enabled = true;

    public RandomFaults(Map<String, Odds> odds, long seed) {
        this.odds = Map.copyOf(odds);
        this.random = new SplittableRandom(seed);
    }

    /** Participants recovered: used for the "retry stuck sagas later" pass. */
    public void heal() {
        enabled = false;
    }

    @Override
    public Fault next(String participant, String operation, String sagaId) {
        Odds o = odds.get(participant + "." + operation);
        if (!enabled || o == null) {
            return Fault.NONE;
        }
        double r = random.nextDouble();
        if (r < o.reject()) {
            return Fault.REJECT;
        }
        if (r < o.reject() + o.transientFailure()) {
            return Fault.TRANSIENT;
        }
        if (r < o.reject() + o.transientFailure() + o.lostReply()) {
            return Fault.LOST_REPLY;
        }
        return Fault.NONE;
    }
}
