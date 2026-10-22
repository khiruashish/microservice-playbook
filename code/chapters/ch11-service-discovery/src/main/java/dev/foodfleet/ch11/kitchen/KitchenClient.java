package dev.foodfleet.ch11.kitchen;

import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/**
 * kitchen-service, called by <b>name</b>, not address. The HTTP service group is named after the
 * service id; with no {@code base-url} (or an {@code lb://} one), Spring Cloud LoadBalancer picks an
 * instance from the DiscoveryClient for every request.
 */
@HttpExchange("/api/kitchen")
public interface KitchenClient {

    @GetExchange("/capacity")
    Capacity capacity();

    record Capacity(String instance, int openTickets) {
    }
}
