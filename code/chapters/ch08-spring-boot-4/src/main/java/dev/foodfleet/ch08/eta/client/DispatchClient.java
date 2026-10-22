package dev.foodfleet.ch08.eta.client;

import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/** dispatch-service: where is the order's rider? Answers 404 while no rider is assigned. */
@HttpExchange("/api/v1/dispatch/orders")
public interface DispatchClient {

    @GetExchange("/{orderId}/rider")
    RiderEstimate rider(@PathVariable String orderId);

    /** {@code riderId} may be null while dispatch is still offering the order to riders. */
    record RiderEstimate(@Nullable String riderId, int minutesToRestaurant, int minutesToCustomer) {
    }
}
