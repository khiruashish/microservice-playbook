package dev.foodfleet.lab19.menu.api;

import dev.foodfleet.lab19.menu.application.PriceQuery;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** menu-service's price endpoint. The price travels as a string with two decimals, as the OpenAPI document says. */
@RestController
public class PriceController {

    public record PriceResponse(String price, boolean available) {
    }

    private final PriceQuery prices;

    public PriceController(PriceQuery prices) {
        this.prices = prices;
    }

    @GetMapping("/restaurants/{restaurantId}/items/{itemId}/price")
    public ResponseEntity<PriceResponse> price(@PathVariable String restaurantId, @PathVariable String itemId) {
        return prices.price(restaurantId, itemId)
                .map(item -> ResponseEntity.ok(new PriceResponse(item.price().setScale(2).toPlainString(), item.available())))
                .orElse(ResponseEntity.notFound().build());
    }
}
