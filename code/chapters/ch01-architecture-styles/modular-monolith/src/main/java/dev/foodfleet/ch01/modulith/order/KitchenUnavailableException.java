package dev.foodfleet.ch01.modulith.order;

/**
 * We could not get an answer at all (timeout, connection refused, 5xx). This failure mode only
 * exists once the kitchen is a remote service. Maps to HTTP 503 with Retry-After.
 */
public class KitchenUnavailableException extends RuntimeException {

    private final String restaurantId;

    public KitchenUnavailableException(String restaurantId, Throwable cause) {
        super("Kitchen availability could not be checked for restaurant " + restaurantId, cause);
        this.restaurantId = restaurantId;
    }

    public String restaurantId() {
        return restaurantId;
    }
}
