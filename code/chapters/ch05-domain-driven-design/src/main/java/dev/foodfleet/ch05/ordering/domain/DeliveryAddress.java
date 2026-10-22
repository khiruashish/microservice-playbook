package dev.foodfleet.ch05.ordering.domain;

import java.util.regex.Pattern;

/**
 * Value object. In the Ordering context an address is "where this order goes": immutable, with no
 * identity of its own. In the Customer context the same words mean a saved, editable, named address
 * ("Home", "Office") with an id. Same term, different model: that's what a bounded context boundary is.
 */
public record DeliveryAddress(String line1, String area, String city, String pincode, String landmark) {

    private static final Pattern PINCODE = Pattern.compile("[1-9][0-9]{5}");

    public DeliveryAddress {
        if (line1 == null || line1.isBlank() || area == null || area.isBlank() || city == null || city.isBlank()) {
            throw new IllegalArgumentException("line1, area and city are required");
        }
        if (pincode == null || !PINCODE.matcher(pincode).matches()) {
            throw new IllegalArgumentException("Invalid pincode: " + pincode);
        }
        landmark = landmark == null ? "" : landmark.strip();
    }
}
