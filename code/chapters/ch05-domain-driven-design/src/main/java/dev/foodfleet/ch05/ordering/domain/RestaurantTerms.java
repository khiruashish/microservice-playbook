package dev.foodfleet.ch05.ordering.domain;

import dev.foodfleet.ch05.ordering.domain.Identifiers.RestaurantId;
import java.util.Objects;

/** The restaurant's commercial terms that ordering must respect, translated from the Catalog context. */
public record RestaurantTerms(RestaurantId restaurantId, boolean acceptingOrders, Money minimumOrder,
                              Money packagingChargePerItem) {

    public RestaurantTerms {
        Objects.requireNonNull(restaurantId, "restaurantId");
        Objects.requireNonNull(minimumOrder, "minimumOrder");
        Objects.requireNonNull(packagingChargePerItem, "packagingChargePerItem");
    }
}
