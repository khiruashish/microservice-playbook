package dev.foodfleet.ch05.ordering.application;

import dev.foodfleet.ch05.ordering.domain.DeliveryAddress;
import dev.foodfleet.ch05.ordering.domain.Identifiers.CustomerId;
import dev.foodfleet.ch05.ordering.domain.Identifiers.MenuItemId;
import dev.foodfleet.ch05.ordering.domain.Identifiers.RestaurantId;
import dev.foodfleet.ch05.ordering.domain.Quantity;
import java.util.List;

/** Command: a request to change the system, in domain types. Named after the use case. */
public record PlaceOrderCommand(CustomerId customerId, RestaurantId restaurantId, DeliveryAddress address,
                                List<Item> items) {

    public PlaceOrderCommand {
        items = List.copyOf(items);
    }

    public record Item(MenuItemId menuItemId, Quantity quantity) {
    }
}
