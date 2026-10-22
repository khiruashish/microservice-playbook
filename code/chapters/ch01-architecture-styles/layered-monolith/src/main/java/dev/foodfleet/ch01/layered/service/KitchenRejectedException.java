package dev.foodfleet.ch01.layered.service;

public class KitchenRejectedException extends RuntimeException {

    private final String restaurantId;

    public KitchenRejectedException(String restaurantId) {
        super("Restaurant " + restaurantId + " is not accepting orders right now");
        this.restaurantId = restaurantId;
    }

    public String restaurantId() {
        return restaurantId;
    }
}
