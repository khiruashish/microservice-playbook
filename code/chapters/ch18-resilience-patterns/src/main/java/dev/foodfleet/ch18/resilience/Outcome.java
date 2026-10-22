package dev.foodfleet.ch18.resilience;

/** How one attempt, or the whole protected call, ended. */
public enum Outcome {
    SUCCESS,
    TIMEOUT,            // we stopped waiting
    UNAVAILABLE,        // the dependency said no (503, connection refused)
    BULKHEAD_FULL,      // rejected locally: too many calls already in flight
    CIRCUIT_OPEN,       // rejected locally: the dependency is known to be failing
    FALLBACK;           // a degraded answer was given instead (cached ETA)

    public boolean isFailure() {
        return this != SUCCESS && this != FALLBACK;
    }
}
