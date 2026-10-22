package dev.foodfleet.ch52.health;

/**
 * Health statuses as Spring Boot defines them, in the order its {@code SimpleStatusAggregator} ranks them: a group is
 * as healthy as its worst member. {@code DOWN} and {@code OUT_OF_SERVICE} map to HTTP 503, which is what makes a
 * Kubernetes HTTP probe fail; everything else is 200.
 */
public enum Status {
    DOWN, OUT_OF_SERVICE, UP, UNKNOWN;

    public int httpStatus() {
        return this == DOWN || this == OUT_OF_SERVICE ? 503 : 200;
    }

    /** The worse of two statuses, in Spring Boot's default order. */
    public static Status worst(Status a, Status b) {
        return a.ordinal() <= b.ordinal() ? a : b;
    }
}
