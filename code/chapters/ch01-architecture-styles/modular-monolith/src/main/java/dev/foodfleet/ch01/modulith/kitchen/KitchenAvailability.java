package dev.foodfleet.ch01.modulith.kitchen;

/** The kitchen's answer to "can you take more orders?". Same shape as kitchen-service's HTTP response. */
public record KitchenAvailability(String restaurantId, boolean accepting, int activeTickets) {
}
