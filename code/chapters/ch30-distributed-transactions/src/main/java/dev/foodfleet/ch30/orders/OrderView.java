package dev.foodfleet.ch30.orders;

/** An order as its owner sees it: PENDING until payments has decided, then CONFIRMED or REJECTED with a reason. */
public record OrderView(long id, String status, String reason) {
}
