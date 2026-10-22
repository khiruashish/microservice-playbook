package dev.foodfleet.ch51.slo;

import java.time.Duration;
import java.util.Optional;

/**
 * Where an SLO stands over its window, from event counts: how much of the error budget is spent, how fast it is going,
 * and what the error budget policy says the team should do about it.
 *
 * @param slo        the objective
 * @param totalEvents events in the window so far
 * @param badEvents   bad events in the window so far
 */
public record ErrorBudget(Slo slo, long totalEvents, long badEvents) {

    /** What FoodFleet's error budget policy asks of the owning team. */
    public enum Policy {
        /** Less than half the budget spent: ship as usual. */
        NORMAL,
        /** Half or more spent: risky changes need a rollback plan and a second reviewer; reliability work is prioritised. */
        CAUTION,
        /** All of it spent: only fixes and reliability work ship until the window recovers. */
        FREEZE
    }

    /** The fraction of the budget spent; above 1 when the SLO is already missed. */
    public double spent() {
        if (totalEvents == 0) {
            return 0;
        }
        return (double) badEvents / totalEvents / slo.errorBudget();
    }

    public double remaining() {
        return 1 - spent();
    }

    /** The observed error ratio over the window. */
    public double errorRatio() {
        return totalEvents == 0 ? 0 : (double) badEvents / totalEvents;
    }

    /**
     * How long until the budget is gone if the recent burn rate continues, measured from now; empty when the recent
     * burn rate is zero or the budget is already spent.
     */
    public Optional<Duration> timeToExhaustion(double recentBurnRate) {
        if (recentBurnRate <= 0 || remaining() <= 0) {
            return Optional.empty();
        }
        // at burn rate b, the whole budget takes window / b; the remaining share takes that much less
        double seconds = remaining() * slo.window().toSeconds() / recentBurnRate;
        return Optional.of(Duration.ofSeconds(Math.round(seconds)));
    }

    public Policy policy() {
        double s = spent();
        if (s >= 1) {
            return Policy.FREEZE;
        }
        return s >= 0.5 ? Policy.CAUTION : Policy.NORMAL;
    }
}
