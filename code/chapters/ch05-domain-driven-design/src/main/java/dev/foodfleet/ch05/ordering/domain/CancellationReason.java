package dev.foodfleet.ch05.ordering.domain;

/** Ubiquitous language: the reasons operations, support and finance actually use. */
public enum CancellationReason {
    CUSTOMER_CHANGED_MIND,
    RESTAURANT_REJECTED,
    ITEM_UNAVAILABLE,
    NO_RIDER_AVAILABLE,
    PAYMENT_FAILED
}
