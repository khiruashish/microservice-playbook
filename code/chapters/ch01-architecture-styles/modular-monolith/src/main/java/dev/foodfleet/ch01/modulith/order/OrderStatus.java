package dev.foodfleet.ch01.modulith.order;

/** Later chapters (sagas, ch14) extend this lifecycle; chapter 1 only needs the first states. */
public enum OrderStatus {
    PLACED,
    CANCELLED
}
