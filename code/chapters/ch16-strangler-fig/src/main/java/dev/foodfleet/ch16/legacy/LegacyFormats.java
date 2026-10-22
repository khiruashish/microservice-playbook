package dev.foodfleet.ch16.legacy;

import java.time.format.DateTimeFormatter;

/** Formats the monolith uses in its tables and method signatures. */
public final class LegacyFormats {

    /** IST wall-clock time, as stored in ORDERS.DLV_TS and passed to pricing. */
    public static final DateTimeFormatter LEGACY_TIME = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    private LegacyFormats() {
    }
}
