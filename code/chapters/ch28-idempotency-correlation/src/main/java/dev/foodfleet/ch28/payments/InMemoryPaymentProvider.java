package dev.foodfleet.ch28.payments;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** A provider that deduplicates by idempotency key, as real ones do, and counts actual charges. */
public class InMemoryPaymentProvider implements PaymentProvider {

    private final Map<String, String> chargesByKey = new ConcurrentHashMap<>();
    private final AtomicInteger charges = new AtomicInteger();

    @Override
    public String charge(String idempotencyKey, PaymentRequest request) {
        return chargesByKey.computeIfAbsent(idempotencyKey, k -> "ch_" + charges.incrementAndGet());
    }

    public int charges() {
        return charges.get();
    }
}
