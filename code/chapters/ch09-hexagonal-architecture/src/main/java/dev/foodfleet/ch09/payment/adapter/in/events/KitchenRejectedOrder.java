package dev.foodfleet.ch09.payment.adapter.in.events;

/**
 * Integration event from kitchen-service. Here it arrives as a Spring application event; in
 * production the same adapter consumes it from Kafka (chapter 25). The core doesn't care which.
 */
public record KitchenRejectedOrder(String orderId, String reason) {
}
