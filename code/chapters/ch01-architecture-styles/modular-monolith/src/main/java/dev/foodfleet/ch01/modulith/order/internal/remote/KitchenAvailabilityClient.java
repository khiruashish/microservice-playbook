package dev.foodfleet.ch01.modulith.order.internal.remote;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/**
 * Declarative HTTP client (Spring Framework 7 HTTP Service Client) for the extracted kitchen-service.
 * Spring generates the implementation. Base URL and timeouts come from
 * {@code spring.http.serviceclient.kitchen.*}.
 */
@HttpExchange("/api/v1/kitchens")
public interface KitchenAvailabilityClient {

    @GetExchange("/{restaurantId}/availability")
    AvailabilityResponse availability(@PathVariable String restaurantId);

    /**
     * The order module's own copy of the kitchen-service contract. It is deliberately not shared
     * through a common library. Each side owns its model, and contract tests (chapter 63) keep
     * them compatible.
     */
    record AvailabilityResponse(String restaurantId, boolean accepting, int activeTickets) {
    }
}
