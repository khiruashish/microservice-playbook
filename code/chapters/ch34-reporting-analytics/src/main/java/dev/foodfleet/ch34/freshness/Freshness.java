package dev.foodfleet.ch34.freshness;

import java.time.Duration;
import java.time.Instant;

/**
 * How current a report is. A reporting store is always behind its sources; a report that doesn't say by how much
 * invites decisions on numbers that stopped updating hours ago (measured: with the CDC consumer stopped, the
 * reporting copy fell 60 s behind in 60 s and nothing in the data itself showed it).
 *
 * @param asOf the event time of the newest change included, or null if nothing has arrived yet
 * @param lag how far behind now that is
 * @param stale whether the lag exceeds the data product's freshness objective
 */
public record Freshness(Instant asOf, Duration lag, boolean stale) {

    public static Freshness of(Instant asOf, Instant now, Duration objective) {
        if (asOf == null) {
            return new Freshness(null, null, true);
        }
        Duration lag = Duration.between(asOf, now);
        if (lag.isNegative()) {
            lag = Duration.ZERO;                           // clock skew between the producer and us
        }
        return new Freshness(asOf, lag, lag.compareTo(objective) > 0);
    }
}
