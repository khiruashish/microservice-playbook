package dev.foodfleet.ch44.degrade;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A dimmer for an optional feature (recommendations on the menu page). Callers ask {@link #shouldTry()} before calling
 * the optional dependency and report each outcome; once per interval {@link #adjust()} looks at the share of calls
 * that were slow or failed and dims multiplicatively when it is too high, brightens additively when it isn't (AIMD).
 * Turning a feature down removes its load, which is what lets the dependency recover; a timeout alone doesn't.
 */
public final class Brownout {

    private final double maxBadShare;
    private final double floor;
    private final AtomicLong good = new AtomicLong();
    private final AtomicLong bad = new AtomicLong();
    private volatile double level = 1.0;

    /**
     * @param maxBadShare share of slow-or-failed calls above which the feature dims, e.g. 0.1
     * @param floor       the lowest level: a trickle of calls keeps measuring the dependency, e.g. 0.05
     */
    public Brownout(double maxBadShare, double floor) {
        this.maxBadShare = maxBadShare;
        this.floor = floor;
    }

    public boolean shouldTry() {
        return ThreadLocalRandom.current().nextDouble() < level;
    }

    public void succeeded() {
        good.incrementAndGet();
    }

    public void failedOrSlow() {
        bad.incrementAndGet();
    }

    /** Called once per interval (FoodFleet: every second). */
    public void adjust() {
        long g = good.getAndSet(0);
        long b = bad.getAndSet(0);
        if (g + b == 0) {
            return;
        }
        double badShare = (double) b / (g + b);
        level = badShare > maxBadShare ? Math.max(floor, level * 0.5) : Math.min(1.0, level + 0.1);
    }

    public double level() {
        return level;
    }
}
