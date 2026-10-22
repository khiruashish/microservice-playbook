package dev.foodfleet.ch21.orders;

import dev.foodfleet.ch21.paging.Cursor;
import java.time.Instant;

/** One order as the history screen shows it. {@code version} changes on every update: it is the ETag. */
public record OrderSummary(String id, String customerId, String restaurant, long totalPaise, String status,
                           Instant placedAt, String deliveryInstructions, long version) {

    public Cursor cursor() {
        return new Cursor(placedAt, id);
    }
}
