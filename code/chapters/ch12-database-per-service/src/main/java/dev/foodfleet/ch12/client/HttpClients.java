package dev.foodfleet.ch12.client;

import dev.foodfleet.ch12.composition.Ports;
import java.util.List;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/** The owners' APIs, including the batch endpoints composition needs. */
public final class HttpClients {

    private HttpClients() {
    }

    @HttpExchange("/api/customers")
    public interface OrderingApi {
        @GetExchange("/{customerId}/orders")
        List<Ports.OrderRow> recent(@PathVariable String customerId, @RequestParam int limit);
    }

    /** GET /api/restaurants?ids=a,b,c — one call for a whole page of orders. */
    @HttpExchange("/api/restaurants")
    public interface CatalogApi {
        @GetExchange
        List<Ports.RestaurantInfo> byIds(@RequestParam List<String> ids);
    }

    @HttpExchange("/api/deliveries")
    public interface DeliveryApi {
        @GetExchange
        List<Ports.DeliveryInfo> byOrderIds(@RequestParam("orderIds") List<String> orderIds);
    }
}
