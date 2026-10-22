package dev.foodfleet.ch01.modulith.kitchen.internal;

import dev.foodfleet.ch01.modulith.kitchen.KitchenOperations;
import dev.foodfleet.ch01.modulith.order.RestaurantAvailability;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * The in-process side of the extraction seam. The kitchen module implements the order module's
 * port with a plain method call: nanoseconds, no partial failure, no serialization.
 */
@Component
@ConditionalOnProperty(name = "foodfleet.kitchen.mode", havingValue = "in-process", matchIfMissing = true)
class InProcessRestaurantAvailability implements RestaurantAvailability {

    private final KitchenOperations kitchen;

    InProcessRestaurantAvailability(KitchenOperations kitchen) {
        this.kitchen = kitchen;
    }

    @Override
    public boolean canAccept(String restaurantId) {
        return kitchen.availability(restaurantId).accepting();
    }
}
