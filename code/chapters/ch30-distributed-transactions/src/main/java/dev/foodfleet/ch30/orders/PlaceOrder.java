package dev.foodfleet.ch30.orders;

/** What the app sends to place an order. */
public record PlaceOrder(long customerId, long itemId, int amountPaise) {

    public PlaceOrder {
        if (customerId <= 0 || itemId <= 0) {
            throw new IllegalArgumentException("customerId and itemId must be positive");
        }
        if (amountPaise <= 0) {
            throw new IllegalArgumentException("amountPaise must be positive");
        }
    }
}
