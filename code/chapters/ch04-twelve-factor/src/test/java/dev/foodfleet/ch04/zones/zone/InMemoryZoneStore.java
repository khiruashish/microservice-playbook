package dev.foodfleet.ch04.zones.zone;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Test double for the backing service: same port, no database. */
public final class InMemoryZoneStore implements ZoneStore {

    private final Map<String, Zone> zones = new ConcurrentHashMap<>();

    public InMemoryZoneStore with(Zone... seed) {
        for (Zone zone : seed) {
            zones.put(zone.pincode(), zone);
        }
        return this;
    }

    @Override
    public Optional<Zone> find(String pincode) {
        return Optional.ofNullable(zones.get(pincode));
    }

    @Override
    public int upsertAll(List<Zone> batch) {
        batch.forEach(zone -> zones.put(zone.pincode(), zone));
        return batch.size();
    }

    @Override
    public long count() {
        return zones.size();
    }
}
