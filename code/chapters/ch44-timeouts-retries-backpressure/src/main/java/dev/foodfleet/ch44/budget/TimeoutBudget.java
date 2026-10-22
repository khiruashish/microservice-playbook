package dev.foodfleet.ch44.budget;

import java.time.Duration;
import java.util.Optional;

/**
 * How long one call may take, given what is left of the caller's deadline. Each hop has its own ceiling (set from the
 * dependency's latency percentiles); the call gets the smaller of that ceiling and the time left, minus a reserve for
 * the work this service still has to do after the call returns. No time left means no call: fail now, not later.
 */
public final class TimeoutBudget {

    private final Duration ceiling;
    private final Duration reserve;
    private final Duration minimum;

    /**
     * @param ceiling this hop's own limit, e.g. a little above the dependency's p99.9
     * @param reserve time kept back for this service's own work after the call
     * @param minimum below this a call can't succeed, so it isn't made
     */
    public TimeoutBudget(Duration ceiling, Duration reserve, Duration minimum) {
        this.ceiling = ceiling;
        this.reserve = reserve;
        this.minimum = minimum;
    }

    /** The timeout for the next call, or empty if there is no point making it. */
    public Optional<Duration> next(Duration remaining) {
        Duration available = remaining.minus(reserve);
        Duration timeout = available.compareTo(ceiling) < 0 ? available : ceiling;
        return timeout.compareTo(minimum) < 0 ? Optional.empty() : Optional.of(timeout);
    }
}
