package dev.foodfleet.ch10.bff.client;

import java.time.Instant;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/**
 * The four services behind the tracking screen, as HTTP service clients. Each record binds only
 * the fields this screen needs (tolerant reader): the services return much more.
 */
public final class Downstream {

    private Downstream() {
    }

    @HttpExchange("/api/orders")
    public interface OrderingClient {
        @GetExchange("/{orderId}")
        OrderSummary order(@PathVariable String orderId);
    }

    /** Version 2.0 is sent automatically: spring.http.serviceclient.eta.apiversion.* */
    @HttpExchange("/api/orders")
    public interface EtaClient {
        @GetExchange("/{orderId}/eta")
        Eta eta(@PathVariable String orderId);
    }

    @HttpExchange("/api/restaurants")
    public interface CatalogClient {
        @GetExchange("/{restaurantId}")
        Restaurant restaurant(@PathVariable String restaurantId);
    }

    @HttpExchange("/api/v1/dispatch/orders")
    public interface DispatchClient {
        @GetExchange("/{orderId}/rider")
        Rider rider(@PathVariable String orderId);
    }

    public record OrderSummary(String orderId, String restaurantId, String status, int itemCount, long totalPaise) {
    }

    public record Eta(Window window, Instant estimatedArrival, boolean degraded) {
        public record Window(int from, int to) {
        }
    }

    public record Restaurant(String restaurantId, String name, String area) {
    }

    public record Rider(String riderId, String name, String vehicle) {
    }
}
