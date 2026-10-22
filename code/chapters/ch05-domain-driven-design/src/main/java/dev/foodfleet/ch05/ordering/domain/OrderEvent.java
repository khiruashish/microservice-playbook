package dev.foodfleet.ch05.ordering.domain;

import dev.foodfleet.ch05.ordering.domain.Identifiers.CustomerId;
import dev.foodfleet.ch05.ordering.domain.Identifiers.OrderId;
import dev.foodfleet.ch05.ordering.domain.Identifiers.RestaurantId;
import java.time.Instant;

/**
 * Domain events: facts the business cares about, named in the past tense in the ubiquitous
 * language. Sealed, so a {@code switch} over them is checked for completeness by the compiler.
 */
public sealed interface OrderEvent {

    OrderId orderId();

    Instant occurredAt();

    record OrderPlaced(OrderId orderId, CustomerId customerId, RestaurantId restaurantId, Money total,
                       int itemCount, Instant occurredAt) implements OrderEvent {
    }

    record DeliveryAddressChanged(OrderId orderId, DeliveryAddress newAddress, Instant occurredAt)
            implements OrderEvent {
    }

    record OrderAccepted(OrderId orderId, Instant occurredAt) implements OrderEvent {
    }

    record OrderDispatched(OrderId orderId, String riderId, Instant occurredAt) implements OrderEvent {
    }

    record OrderDelivered(OrderId orderId, Instant occurredAt) implements OrderEvent {
    }

    /** @param refund amount owed back to the customer; zero for drafts */
    record OrderCancelled(OrderId orderId, CancellationReason reason, Money refund, Instant occurredAt)
            implements OrderEvent {
    }
}
