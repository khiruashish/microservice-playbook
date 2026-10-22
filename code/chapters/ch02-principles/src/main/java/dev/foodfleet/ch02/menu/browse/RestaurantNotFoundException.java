package dev.foodfleet.ch02.menu.browse;

public class RestaurantNotFoundException extends RuntimeException {

    private final String restaurantId;

    public RestaurantNotFoundException(String restaurantId) {
        super("No restaurant " + restaurantId);
        this.restaurantId = restaurantId;
    }

    public String restaurantId() {
        return restaurantId;
    }
}
