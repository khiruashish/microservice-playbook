package dev.foodfleet.ch52.availability;

import dev.foodfleet.ch52.health.HealthCheck;
import dev.foodfleet.ch52.health.Status;

import java.util.Map;

/**
 * The application's own view of whether it is alive and whether it should get traffic, like Spring Boot's
 * {@code ApplicationAvailability}: liveness is CORRECT unless the application knows its internal state is broken
 * beyond repair; readiness is ACCEPTING_TRAFFIC once startup is complete and REFUSING_TRAFFIC while it shuts down
 * (or while it chooses to shed load). Neither looks at other systems.
 */
public final class Availability {

    public enum Liveness { CORRECT, BROKEN }

    public enum Readiness { ACCEPTING_TRAFFIC, REFUSING_TRAFFIC }

    private volatile Liveness liveness = Liveness.CORRECT;
    private volatile Readiness readiness = Readiness.REFUSING_TRAFFIC;

    public Liveness liveness() {
        return liveness;
    }

    public Readiness readiness() {
        return readiness;
    }

    public void liveness(Liveness state) {
        this.liveness = state;
    }

    public void readiness(Readiness state) {
        this.readiness = state;
    }

    /** Spring Boot's {@code livenessState} contributor: BROKEN is DOWN. */
    public HealthCheck livenessCheck() {
        return () -> new HealthCheck.Result(liveness == Liveness.CORRECT ? Status.UP : Status.DOWN, Map.of());
    }

    /** Spring Boot's {@code readinessState} contributor: REFUSING_TRAFFIC is OUT_OF_SERVICE. */
    public HealthCheck readinessCheck() {
        return () -> new HealthCheck.Result(readiness == Readiness.ACCEPTING_TRAFFIC ? Status.UP : Status.OUT_OF_SERVICE, Map.of());
    }
}
