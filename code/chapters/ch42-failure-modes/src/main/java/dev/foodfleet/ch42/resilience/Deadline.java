package dev.foodfleet.ch42.resilience;

import java.time.Duration;
import java.util.Optional;

/**
 * The time by which the original caller stops waiting, carried on every hop as an absolute time
 * ({@code X-Request-Deadline: <epoch millis>}). Each service works only while it is in the future: work for a caller
 * who has already given up is the fuel of retry storms and metastable failures. Clock skew between hosts eats into
 * it, so deadlines are coarse (hundreds of milliseconds), and a hop never extends one.
 */
public record Deadline(long epochMillis) {

    public static final String HEADER = "X-Request-Deadline";

    public static Deadline after(Duration budget, long nowMillis) {
        return new Deadline(nowMillis + budget.toMillis());
    }

    public static Optional<Deadline> parse(String header) {
        if (header == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(new Deadline(Long.parseLong(header.strip())));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    public Duration remaining(long nowMillis) {
        return Duration.ofMillis(Math.max(0, epochMillis - nowMillis));
    }

    public boolean expired(long nowMillis) {
        return nowMillis >= epochMillis;
    }

    /** The earlier of this deadline and now + {@code limit}: a hop may shorten the budget, never lengthen it. */
    public Deadline min(Duration limit, long nowMillis) {
        return new Deadline(Math.min(epochMillis, nowMillis + limit.toMillis()));
    }

    public String header() {
        return Long.toString(epochMillis);
    }
}
