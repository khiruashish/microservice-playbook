package dev.foodfleet.ch04.zones.zone;

import java.util.List;
import java.util.Optional;

/** Port to the zone backing service. The implementation is chosen by configuration, not code (factor IV). */
public interface ZoneStore {

    Optional<Zone> find(String pincode);

    /** Inserts or updates every zone; returns the number of rows written. */
    int upsertAll(List<Zone> zones);

    long count();
}
