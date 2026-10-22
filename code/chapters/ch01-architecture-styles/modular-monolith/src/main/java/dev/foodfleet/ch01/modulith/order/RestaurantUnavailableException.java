package dev.foodfleet.ch01.modulith.order;

/** The restaurant answered, and the answer is "no" (paused, or at capacity). Maps to HTTP 409. */
public class RestaurantUnavailableException extends RuntimeException {

    private final String restaurantId;

    public RestaurantUnavailableException(String restaurantId) {
        super("Restaurant " + restaurantId + " is not accepting orders right now");
        this.restaurantId = restaurantId;
    }

    public String restaurantId() {
        return restaurantId;
    }
}
