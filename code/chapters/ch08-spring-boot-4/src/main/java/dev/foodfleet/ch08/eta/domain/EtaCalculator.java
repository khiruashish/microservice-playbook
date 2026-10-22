package dev.foodfleet.ch08.eta.domain;

import java.time.Duration;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Plain Java, no Spring: turns kitchen and rider estimates into the window a customer sees.
 *
 * <ul>
 *   <li>Pickup happens when both the food and the rider are at the restaurant:
 *       {@code max(prep, riderToRestaurant)}.</li>
 *   <li>Arrival = pickup + travel to the customer.</li>
 *   <li>The window is ±10% (at least 3 minutes) with live rider data, and ±25% (at least 5 minutes)
 *       when dispatch is unavailable and the zone's average travel time is used instead.</li>
 * </ul>
 */
public final class EtaCalculator {

    /** {@code rider} is {@code null} when no rider is assigned yet or dispatch is unavailable. */
    public Eta calculate(int prepMinutes, @Nullable RiderLeg rider, int fallbackTravelMinutes, Instant now) {
        if (prepMinutes < 0 || fallbackTravelMinutes < 1) {
            throw new IllegalArgumentException("prepMinutes must be >= 0 and fallbackTravelMinutes >= 1");
        }
        boolean degraded;
        int waitForRider;
        int travel;
        if (rider == null) {                       // checked explicitly, so NullAway can prove the else branch
            degraded = true;
            waitForRider = 0;                      // assume a rider is waiting when the food is ready
            travel = fallbackTravelMinutes;
        } else {
            degraded = false;
            waitForRider = Math.max(0, rider.minutesToRestaurant() - prepMinutes);
            travel = rider.minutesToCustomer();
        }
        int total = prepMinutes + waitForRider + travel;

        int margin = degraded
                ? Math.max(5, Math.round(total * 0.25f))
                : Math.max(3, Math.round(total * 0.10f));
        EtaWindow window = new EtaWindow(Math.max(1, total - margin), total + margin);

        return new Eta(total, window, now.plus(Duration.ofMinutes(total)),
                new Breakdown(prepMinutes, waitForRider, travel), degraded);
    }

    /** What dispatch knows about the order's rider. */
    public record RiderLeg(@Nullable String riderId, int minutesToRestaurant, int minutesToCustomer) {
        public RiderLeg {
            if (minutesToRestaurant < 0 || minutesToCustomer < 1) {
                throw new IllegalArgumentException("Invalid rider leg: " + minutesToRestaurant + "/" + minutesToCustomer);
            }
        }
    }

    public record Breakdown(int prepMinutes, int waitForRiderMinutes, int travelMinutes) {
    }

    /**
     * @param minutes           best single estimate (what v1 clients get)
     * @param estimatedArrival  {@code now + minutes}; Jackson 3 writes it as an ISO-8601 string by default
     * @param degraded          true when the estimate used fallbacks instead of live rider data
     */
    public record Eta(int minutes, EtaWindow window, Instant estimatedArrival, Breakdown breakdown, boolean degraded) {
    }
}
