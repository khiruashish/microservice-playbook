package dev.foodfleet.ch30.payments;

/** The event payment-service receives for each order (from the order outbox). */
public record OrderPlaced(long orderId, long customerId, long itemId, int amountPaise) {
}
