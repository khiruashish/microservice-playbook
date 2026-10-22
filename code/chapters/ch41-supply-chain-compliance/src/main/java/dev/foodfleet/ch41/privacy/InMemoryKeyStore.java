package dev.foodfleet.ch41.privacy;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** For tests and measurements; in FoodFleet the wrapped keys are a PostgreSQL table excluded from data exports. */
public final class InMemoryKeyStore implements CryptoShredder.KeyStore {

    private final ConcurrentMap<String, byte[]> keys = new ConcurrentHashMap<>();

    @Override
    public Optional<byte[]> get(String subject) {
        return Optional.ofNullable(keys.get(subject));
    }

    @Override
    public void put(String subject, byte[] wrappedKey) {
        keys.put(subject, wrappedKey.clone());
    }

    @Override
    public boolean delete(String subject) {
        return keys.remove(subject) != null;
    }

    public int size() {
        return keys.size();
    }
}
