package dev.foodfleet.lab19.order;

import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.web.client.RestClient;

/** order-service's side: what it reads from menu-service's price, and nothing more. */
public class MenuPriceClient {

    public record Price(BigDecimal amount, boolean available) {
    }

    private record Body(String price, boolean available) {
    }

    private final RestClient menu;

    public MenuPriceClient(RestClient menu) {
        this.menu = menu;
    }

    public Optional<Price> price(String restaurantId, String itemId) {
        Body body = menu.get().uri("/restaurants/{r}/items/{i}/price", restaurantId, itemId).retrieve()
                .onStatus(status -> status.value() == 404, (request, response) -> { })      // unknown item: empty
                .body(Body.class);
        return body == null ? Optional.empty() : Optional.of(new Price(new BigDecimal(body.price()), body.available()));
    }
}
