package dev.foodfleet.ch31.reads;

import java.util.Optional;

/** Where the primary's log is, and how far the replica has replayed it. */
public interface Positions {

    /** The primary's current WAL position: taken after a write, it covers that write. */
    ConsistencyToken primary();

    /** How far the replica has replayed; empty if it can't tell (not in recovery, unreachable). */
    Optional<ConsistencyToken> replica();
}
