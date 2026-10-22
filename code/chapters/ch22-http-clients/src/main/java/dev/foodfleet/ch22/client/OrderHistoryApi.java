package dev.foodfleet.ch22.client;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/**
 * order-history-service's API (chapter 21) as an HTTP service client: an interface, no
 * implementation. Spring generates the client; the group's configuration supplies base URL,
 * timeouts and error handling.
 */
@HttpExchange("/api")
public interface OrderHistoryApi {

    record OrderView(String id, String restaurant, long totalPaise, String status, String placedAt, String self) {
    }

    record OrderPage(List<OrderView> items, String next) {
    }

    record Order(String id, String customerId, String restaurant, long totalPaise, String status, String placedAt,
                 String deliveryInstructions, long version) {
    }

    @GetExchange("/customers/{customerId}/orders")
    OrderPage page(@PathVariable String customerId, @RequestParam int limit,
                   @RequestParam(required = false) String cursor);

    /** ResponseEntity, so callers see the ETag and a 304 (which has no body). */
    @GetExchange("/orders/{orderId}")
    ResponseEntity<Order> order(@PathVariable String orderId,
                                @RequestHeader(name = "If-None-Match", required = false) String ifNoneMatch);
}
