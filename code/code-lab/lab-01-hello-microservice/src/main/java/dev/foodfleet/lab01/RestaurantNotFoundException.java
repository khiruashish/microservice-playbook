package dev.foodfleet.lab01;

public class RestaurantNotFoundException extends RuntimeException {

    private final String restaurantId;

    public RestaurantNotFoundException(String restaurantId) {
        super("No restaurant with id " + restaurantId);
        this.restaurantId = restaurantId;
    }

    public String restaurantId() {
        return restaurantId;
    }
}
