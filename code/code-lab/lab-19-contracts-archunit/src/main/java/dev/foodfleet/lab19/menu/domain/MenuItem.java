package dev.foodfleet.lab19.menu.domain;

import java.math.BigDecimal;

/** The domain: no Spring, no web types, no I/O (ADR-009). The architecture rules keep it that way. */
public record MenuItem(String restaurantId, String itemId, BigDecimal price, boolean available) {
}
