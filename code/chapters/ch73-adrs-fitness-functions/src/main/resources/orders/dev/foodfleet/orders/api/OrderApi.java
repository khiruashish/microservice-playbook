package dev.foodfleet.orders.api;

import java.util.Optional;

/** The service's published API: what other services compile against. */
public interface OrderApi {

    record OrderView(String id, String restaurant, int lines, long totalInPaise, String placedAt, String status) {
    }

    Optional<OrderView> get(String id);

    boolean cancel(String id);
}
