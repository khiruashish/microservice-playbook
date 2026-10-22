package dev.foodfleet.ch12.composition;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * The three services that own the data an "order history" screen needs. Each owns its own
 * database; the only way in is its API. Batch methods exist because the composer asks for many
 * ids at once: an API without them forces N+1 calls.
 */
public final class Ports {

    private Ports() {
    }

    /** ordering-service owns orders. */
    public interface Ordering {
        List<OrderRow> recentOrders(String customerId, int limit);
    }

    /** catalog-service owns restaurants. Unknown ids are simply absent from the result. */
    public interface Catalog {
        Map<String, RestaurantInfo> restaurants(Collection<String> restaurantIds);
    }

    /** delivery-service owns deliveries. Orders not yet dispatched are absent. */
    public interface Delivery {
        Map<String, DeliveryInfo> deliveries(Collection<String> orderIds);
    }

    public record OrderRow(String orderId, String restaurantId, Instant placedAt, long totalPaise, String status) {
    }

    public record RestaurantInfo(String restaurantId, String name, String area) {
    }

    public record DeliveryInfo(String orderId, String riderName, Instant deliveredAt) {
    }
}
