package dev.foodfleet.lab14.mvc;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

/**
 * order-service's quote: menu, then pricing, then delivery fee, one after the other (each needs the previous answer).
 * Plain blocking code. With spring.threads.virtual.enabled each request runs on its own virtual thread, and a
 * thread waiting on a call costs a few KB of heap instead of an OS thread.
 */
@RestController
class QuoteController {

    private final RestClient downstream;

    QuoteController(RestClient.Builder builder, @Value("${foodfleet.downstream-url}") String downstreamUrl) {
        this.downstream = builder.baseUrl(downstreamUrl).build();
    }

    @GetMapping("/quotes/{restaurantId}")
    Map<String, Object> quote(@PathVariable String restaurantId) {
        Map<?, ?> menu = downstream.get().uri("/menu/{id}", restaurantId).retrieve().body(Map.class);
        Map<?, ?> price = downstream.post().uri("/price").body(menu).retrieve().body(Map.class);
        Map<?, ?> fee = downstream.get().uri("/delivery-fee?subtotal={s}", price.get("subtotal")).retrieve().body(Map.class);
        return Map.of("restaurantId", restaurantId, "subtotal", price.get("subtotal"), "deliveryFee", fee.get("fee"));
    }
}
