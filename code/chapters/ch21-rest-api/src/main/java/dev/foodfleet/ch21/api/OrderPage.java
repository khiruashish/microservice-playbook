package dev.foodfleet.ch21.api;

import dev.foodfleet.ch21.orders.OrderSummary;
import java.util.List;

/**
 * A page of a customer's orders. {@code next} is the URL of the next page, or null on the last one:
 * clients follow it rather than building URLs (the useful core of hypermedia).
 */
public record OrderPage(List<OrderView> items, String next) {

    public record OrderView(String id, String restaurant, long totalPaise, String status, String placedAt,
                            String self) {
        static OrderView of(OrderSummary o) {
            return new OrderView(o.id(), o.restaurant(), o.totalPaise(), o.status(), o.placedAt().toString(),
                    "/api/orders/" + o.id());
        }
    }
}
