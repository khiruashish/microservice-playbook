package dev.foodfleet.ch08.eta.client;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/**
 * kitchen-service, declared as a Java interface. Spring Framework 7 generates the implementation;
 * Spring Boot 4 configures it from {@code spring.http.serviceclient.kitchen.*} (base URL, timeouts).
 */
@HttpExchange("/api/v1/kitchen/orders")
public interface KitchenClient {

    @GetExchange("/{orderId}/prep")
    PrepStatus prep(@PathVariable String orderId);

    /** Tolerant reader: only the fields we use; unknown fields are ignored by Boot's JsonMapper. */
    record PrepStatus(String orderId, int minutesRemaining) {
    }
}
