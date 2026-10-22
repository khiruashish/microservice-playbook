package dev.foodfleet.ch16.strangler;

import java.nio.charset.StandardCharsets;

/**
 * Stable percentage cohorts: a customer is always in the same bucket (0–99), so at 5% the same 5% of
 * customers see the new service on every request, and raising to 25% keeps those 5% in.
 */
public final class Cohorts {

    private Cohorts() {
    }

    public static int bucket(String customerId) {
        long h = 0xcbf29ce484222325L;                                // FNV-1a, 64 bit
        for (byte b : customerId.getBytes(StandardCharsets.UTF_8)) {
            h ^= b & 0xff;
            h *= 0x100000001b3L;
        }
        h ^= h >>> 33;                                               // spread the bits
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        return (int) Math.floorMod(h, 100L);
    }

    public static boolean inRollout(String customerId, int percent) {
        return bucket(customerId) < percent;
    }
}
