package dev.foodfleet.ch65.sample.infrastructure;

/** The orders table's name and limits. */
public final class OrderTable {

    public static final String NAME = "orders";
    public static final int MAX_LINES = 20;

    private OrderTable() {
    }

    public static int maxLines() {
        return MAX_LINES;
    }
}
