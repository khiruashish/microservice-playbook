package dev.foodfleet.ch16.acl;

/** The result of translating one legacy row: a clean domain object, or a reason it can't be one. */
public sealed interface Translation {

    record Translated(Order order) implements Translation {
    }

    /** Rows that can't be translated go to a quarantine table for a human, never silently guessed. */
    record Quarantined(String legacyId, String reason) implements Translation {
    }
}
