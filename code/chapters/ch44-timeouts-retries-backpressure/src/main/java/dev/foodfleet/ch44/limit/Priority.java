package dev.foodfleet.ch44.limit;

import java.util.Locale;

/**
 * FoodFleet's request classes. The edge sets {@code X-FoodFleet-Priority} from the route; services trust it only from
 * inside the mesh. CRITICAL: placing and paying for orders, rider location. SHEDDABLE: browsing, recommendations,
 * history, analytics beacons.
 */
public enum Priority {
    CRITICAL, SHEDDABLE;

    public static final String HEADER = "X-FoodFleet-Priority";

    /** Unknown or missing means sheddable: a request has to be marked to be protected. */
    public static Priority of(String header) {
        if (header == null) {
            return SHEDDABLE;
        }
        return "critical".equals(header.trim().toLowerCase(Locale.ROOT)) ? CRITICAL : SHEDDABLE;
    }

    public String partition() {
        return name().toLowerCase(Locale.ROOT);
    }
}
