package dev.foodfleet.ch05.ordering.application;

import dev.foodfleet.ch05.ordering.domain.DeliveryAddress;
import dev.foodfleet.ch05.ordering.domain.Money;

/** Port to the Delivery Zones context (zone-service, chapter 4): the fee to deliver to an address. */
@FunctionalInterface
public interface DeliveryFeeQuote {

    Money feeFor(DeliveryAddress address);
}
