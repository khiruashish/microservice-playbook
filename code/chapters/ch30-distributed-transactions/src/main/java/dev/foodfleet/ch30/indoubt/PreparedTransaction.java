package dev.foodfleet.ch30.indoubt;

import java.time.Instant;

/** A row of pg_prepared_xacts: a transaction that voted "yes" in a two-phase commit and awaits the decision. */
public record PreparedTransaction(String gid, Instant prepared, String owner, String database) {
}
