package dev.foodfleet.ch61.order;

import java.math.BigDecimal;

/** menu-service, as order-service sees it. */
public interface MenuClient {

    record Price(BigDecimal price, boolean available) {
    }

    Price price(int restaurantId, String itemId);
}
