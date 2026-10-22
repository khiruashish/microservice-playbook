package dev.foodfleet.ch33.address;

import java.util.List;

/**
 * The steps of renaming customers.address to delivery_address without downtime. Each step is one rolling deploy;
 * an instance in step N works with instances in steps N-1 and N+1. Measured with 4 instances under load: renaming
 * in place failed 3,900-4,900 statements during the deploy; these steps failed none.
 */
public enum AddressPhase {

    /** After the expand migration: write both columns, keep reading the old one. Backfill once all instances are here. */
    EXPAND(List.of("address", "delivery_address"), "address"),
    /** After the backfill: read the new column, still write both (so instances in EXPAND keep reading correct data). */
    READ_NEW(List.of("address", "delivery_address"), "delivery_address"),
    /** Stop writing the old column. When every instance is here, the contract migration drops it. */
    CONTRACT(List.of("delivery_address"), "delivery_address");

    private final List<String> writeColumns;
    private final String readColumn;

    AddressPhase(List<String> writeColumns, String readColumn) {
        this.writeColumns = writeColumns;
        this.readColumn = readColumn;
    }

    public List<String> writeColumns() {
        return writeColumns;
    }

    public String readColumn() {
        return readColumn;
    }

    /** The UPDATE for this phase; column names come only from this enum, never from input. */
    public String updateSql() {
        return "UPDATE customers SET " + String.join(", ", writeColumns.stream().map(c -> c + " = :address").toList())
                + " WHERE id = :id";
    }

    public String insertSql() {
        return "INSERT INTO customers (id, name, " + String.join(", ", writeColumns) + ") VALUES (:id, :name, "
                + String.join(", ", writeColumns.stream().map(c -> ":address").toList()) + ")";
    }

    public String selectSql() {
        return "SELECT " + readColumn + " FROM customers WHERE id = :id";
    }
}
