package dev.foodfleet.ch05.ordering.domain;

import java.util.Objects;

/** Value object: what the customer pays and why. Every component is shown on the bill. */
public record PriceBreakdown(Money itemTotal, Money packaging, Money gst, Money deliveryFee, Money total) {

    public PriceBreakdown {
        Objects.requireNonNull(itemTotal);
        Objects.requireNonNull(packaging);
        Objects.requireNonNull(gst);
        Objects.requireNonNull(deliveryFee);
        Objects.requireNonNull(total);
        if (!itemTotal.plus(packaging).plus(gst).plus(deliveryFee).equals(total)) {
            throw new IllegalArgumentException("Components do not add up to the total");
        }
    }
}
