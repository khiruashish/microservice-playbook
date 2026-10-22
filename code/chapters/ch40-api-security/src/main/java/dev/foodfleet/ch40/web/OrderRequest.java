package dev.foodfleet.ch40.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * What a customer may say when placing an order, and nothing else. No price (the menu decides), no customer id
 * (the token decides), no tenant (the token decides), no status. Jackson is configured to fail on unknown
 * properties for this API (order-service.yml), so {@code "price": 1} or {@code "status": "PAID"} is a 400, not
 * silently ignored or, worse, bound.
 */
public record OrderRequest(
        @NotNull @Pattern(regexp = "r-[0-9]{1,12}") String restaurantId,
        @NotNull @Size(min = 1, max = 50) List<@Valid @NotNull Item> items,
        @Pattern(regexp = "a-[0-9]{1,12}") String addressId,
        @Size(max = 500) String note) {

    public record Item(
            @NotNull @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+){0,7}") @Size(max = 64) String sku,
            @Min(1) @Max(20) int qty) {
    }
}
