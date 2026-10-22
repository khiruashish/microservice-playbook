package dev.foodfleet.lab14.webflux;

import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * The same quote on WebFlux: a chain of non-blocking calls on Reactor Netty's few event-loop threads. Nothing in the
 * chain may block; a blocking call here stalls every request sharing that event loop.
 */
@RestController
class QuoteController {

    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(2);

    private final WebClient downstream;

    QuoteController(WebClient.Builder builder, @Value("${foodfleet.downstream-url}") String downstreamUrl) {
        this.downstream = builder.baseUrl(downstreamUrl).build();
    }

    @GetMapping("/quotes/{restaurantId}")
    Mono<Map<String, Object>> quote(@PathVariable String restaurantId) {
        return downstream.get().uri("/menu/{id}", restaurantId).retrieve().bodyToMono(Map.class).timeout(CALL_TIMEOUT)
                .flatMap(menu -> downstream.post().uri("/price").bodyValue(menu).retrieve().bodyToMono(Map.class).timeout(CALL_TIMEOUT))
                .flatMap(price -> downstream.get().uri("/delivery-fee?subtotal={s}", price.get("subtotal")).retrieve()
                        .bodyToMono(Map.class).timeout(CALL_TIMEOUT)
                        .map(fee -> Map.<String, Object>of("restaurantId", restaurantId,
                                "subtotal", price.get("subtotal"), "deliveryFee", fee.get("fee"))));
    }
}
