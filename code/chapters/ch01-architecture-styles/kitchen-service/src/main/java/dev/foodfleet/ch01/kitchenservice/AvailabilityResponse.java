package dev.foodfleet.ch01.kitchenservice;

/** Provider side of the availability contract. The consumer (order module) keeps its own copy. */
public record AvailabilityResponse(String restaurantId, boolean accepting, int activeTickets) {
}
