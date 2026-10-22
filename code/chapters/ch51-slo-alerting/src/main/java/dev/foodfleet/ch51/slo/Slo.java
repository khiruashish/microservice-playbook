package dev.foodfleet.ch51.slo;

import java.time.Duration;

/**
 * A service level objective: the fraction of good events a service promises over a rolling window, such as "99.9% of
 * checkout requests succeed, over 30 days". The error budget is what's left: 0.1% of requests, or 43.2 minutes of
 * total outage in 30 days.
 *
 * @param service   the service the SLO belongs to, as in the metrics' {@code job} label
 * @param name      a short name for the user journey, such as {@code checkout-availability}
 * @param objective the target fraction of good events, between 0 and 1 (exclusive)
 * @param window    the rolling compliance window
 */
public record Slo(String service, String name, double objective, Duration window) {

    public Slo {
        if (!(objective > 0 && objective < 1)) {
            throw new IllegalArgumentException("objective must be between 0 and 1: " + objective);
        }
        if (window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("window must be positive: " + window);
        }
    }

    /** The fraction of events allowed to be bad: 1 - objective (rounded, so 1 - 0.999 is 0.001, not 0.0010000000000000009). */
    public double errorBudget() {
        return Math.round((1 - objective) * 1e12) / 1e12;
    }

    /** How long a complete outage would take to spend the whole budget. */
    public Duration budgetAsOutage() {
        return Duration.ofMillis(Math.round(window.toMillis() * errorBudget()));
    }

    /** How many of {@code totalEvents} may be bad within the window. */
    public long allowedBadEvents(long totalEvents) {
        return (long) Math.floor(totalEvents * errorBudget() + 1e-9);
    }

    /** How fast an error ratio spends the budget: 1 spends exactly the budget over the window. */
    public double burnRate(double errorRatio) {
        return errorRatio / errorBudget();
    }
}
