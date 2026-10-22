package dev.foodfleet.ch15.orders;

/** ordering-service's {@code orders} table. */
public record OrderRow(String id, String customerId, long totalPaise, Status status) {

    public enum Status { PLACED, CANCELLED }

    public static final String TABLE = "orders";

    /** FoodFleet loyalty: one point per ₹10. */
    public long points() {
        return totalPaise / 1_000;
    }

    public OrderRow cancelled() {
        return new OrderRow(id, customerId, totalPaise, Status.CANCELLED);
    }
}
