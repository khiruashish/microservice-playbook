package dev.foodfleet.ch01.modulith.order.internal.remote;

import dev.foodfleet.ch01.modulith.order.KitchenUnavailableException;
import dev.foodfleet.ch01.modulith.order.RestaurantAvailability;
import dev.foodfleet.ch01.modulith.order.internal.remote.KitchenAvailabilityClient.AvailabilityResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

/**
 * The remote side of the extraction seam: it implements the order module's port by calling
 * kitchen-service over HTTP. It is active only when {@code foodfleet.kitchen.mode=remote}.
 *
 * <p>Policy: <strong>fail closed</strong>. If we cannot reach the kitchen we refuse the order
 * (503, retryable) rather than accept food nobody may cook. Chapter 43 adds a circuit breaker
 * and a short-TTL cached fallback so a brief kitchen-service outage doesn't stop all checkouts.
 */
@Component
@ConditionalOnProperty(name = "foodfleet.kitchen.mode", havingValue = "remote")
public class RemoteRestaurantAvailability implements RestaurantAvailability {

    private final KitchenAvailabilityClient client;

    public RemoteRestaurantAvailability(KitchenAvailabilityClient client) {
        this.client = client;
    }

    @Override
    public boolean canAccept(String restaurantId) {
        try {
            AvailabilityResponse response = client.availability(restaurantId);
            return response != null && response.accepting();
        } catch (HttpClientErrorException.NotFound unknownRestaurant) {
            return false; // a definite business answer: this kitchen does not exist
        } catch (RestClientException unreachable) {
            throw new KitchenUnavailableException(restaurantId, unreachable);
        }
    }
}
