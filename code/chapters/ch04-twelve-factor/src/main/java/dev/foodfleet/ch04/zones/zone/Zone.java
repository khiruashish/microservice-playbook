package dev.foodfleet.ch04.zones.zone;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A delivery zone keyed by Indian 6-digit pincode.
 *
 * @param deliveryFeePaise zone-specific fee, or {@code null} to use the configured default
 */
public record Zone(String pincode, String areaName, boolean serviceable, Long deliveryFeePaise) {

    private static final Pattern PINCODE = Pattern.compile("[1-9][0-9]{5}");

    public Zone {
        Objects.requireNonNull(pincode, "pincode");
        Objects.requireNonNull(areaName, "areaName");
        if (!PINCODE.matcher(pincode).matches()) {
            throw new IllegalArgumentException("Invalid pincode: " + pincode);
        }
        if (areaName.isBlank()) {
            throw new IllegalArgumentException("areaName must not be blank");
        }
        if (deliveryFeePaise != null && deliveryFeePaise < 0) {
            throw new IllegalArgumentException("deliveryFeePaise must be >= 0");
        }
    }

    public static boolean isValidPincode(String candidate) {
        return candidate != null && PINCODE.matcher(candidate).matches();
    }
}
