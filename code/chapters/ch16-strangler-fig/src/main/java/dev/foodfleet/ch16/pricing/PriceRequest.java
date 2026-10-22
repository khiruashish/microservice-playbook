package dev.foodfleet.ch16.pricing;

import java.time.Instant;
import java.util.List;

/** A request to price a basket, in the new model: paise, metres, an instant, an explicit tier. */
public record PriceRequest(String customerId, Tier tier, List<Long> itemsPaise, int distanceMeters,
                           Instant orderedAt, String zone) {

    public enum Tier { NONE, SILVER, GOLD }

    public PriceRequest {
        itemsPaise = List.copyOf(itemsPaise);
    }

    public long itemsTotalPaise() {
        return itemsPaise.stream().mapToLong(Long::longValue).sum();
    }
}
