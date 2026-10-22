package dev.foodfleet.ch11.kitchen;

import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;

/**
 * Discovery is always slightly stale: an instance can die seconds or minutes before every client
 * stops choosing it. A retry that goes back through the load balancer lands on another instance,
 * so the caller sees a slower call instead of an error. Safe here because the call is a read.
 */
@Component
public class KitchenCapacityLookup {

    private final KitchenClient kitchen;

    KitchenCapacityLookup(KitchenClient kitchen) {
        this.kitchen = kitchen;
    }

    @Retryable(includes = ResourceAccessException.class, maxRetries = 1, delay = 10)
    public KitchenClient.Capacity capacity() {
        return kitchen.capacity();
    }
}
