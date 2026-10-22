package dev.foodfleet.ch21.api;

import dev.foodfleet.ch21.orders.OrderStore;
import dev.foodfleet.ch21.orders.OrderSummary;
import dev.foodfleet.ch21.paging.Cursor;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

/**
 * Resources: {@code /api/customers/{id}/orders} (a collection, paged by cursor) and
 * {@code /api/orders/{id}} (one order, cacheable with an ETag, updatable only with If-Match).
 */
@RestController
@RequestMapping("/api")
class OrderHistoryController {

    static final int DEFAULT_LIMIT = 20;
    static final int MAX_LIMIT = 100;

    private final OrderStore orders;

    OrderHistoryController(OrderStore orders) {
        this.orders = orders;
    }

    @GetMapping("/customers/{customerId}/orders")
    ResponseEntity<OrderPage> history(@PathVariable String customerId,
                                      @RequestParam(defaultValue = "" + DEFAULT_LIMIT) int limit,
                                      @RequestParam Optional<String> cursor) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new ApiException(ApiProblem.INVALID_LIMIT, "limit must be between 1 and " + MAX_LIMIT,
                    Map.of("minimum", 1, "maximum", MAX_LIMIT));
        }
        // One extra row tells us whether there is a next page, without a count(*) query.
        List<OrderSummary> rows = orders.page(customerId, cursor.map(Cursor::decode), limit + 1);
        boolean more = rows.size() > limit;
        List<OrderSummary> page = more ? rows.subList(0, limit) : rows;
        String next = more
                ? "/api/customers/%s/orders?limit=%d&cursor=%s".formatted(customerId, limit,
                        page.getLast().cursor().encode())
                : null;
        ResponseEntity.BodyBuilder response = ResponseEntity.ok();
        if (next != null) {
            response.header(HttpHeaders.LINK, "<" + next + ">; rel=\"next\"");   // RFC 8288 web linking
        }
        return response.body(new OrderPage(page.stream().map(OrderPage.OrderView::of).toList(), next));
    }

    @GetMapping("/orders/{orderId}")
    ResponseEntity<OrderSummary> order(@PathVariable String orderId, WebRequest request) {
        OrderSummary order = orders.find(orderId).orElseThrow(() -> new OrderStore.OrderNotFound(orderId));
        String etag = etag(order.version());
        if (request.checkNotModified(etag)) {
            return null;                    // Spring has already set 304 Not Modified and the ETag
        }
        return ResponseEntity.ok()
                .eTag(etag)
                .cacheControl(CacheControl.maxAge(Duration.ZERO).cachePrivate().mustRevalidate())
                .body(order);
    }

    record DeliveryInstructions(String instructions) {
    }

    /** Optimistic concurrency over HTTP: the client must prove which version it is changing. */
    @PatchMapping("/orders/{orderId}/delivery-instructions")
    ResponseEntity<OrderSummary> updateInstructions(@PathVariable String orderId,
                                                    @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                                    @RequestBody DeliveryInstructions body) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new ApiException(ApiProblem.PRECONDITION_REQUIRED,
                    "Send the ETag from GET /api/orders/" + orderId + " in If-Match.");
        }
        long expected = ifMatch.strip().equals("*")        // "*": any current version (the order must exist)
                ? orders.find(orderId).orElseThrow(() -> new OrderStore.OrderNotFound(orderId)).version()
                : ETags.versionIn(ifMatch);
        OrderSummary updated = orders.updateDeliveryInstructions(orderId, expected, body.instructions());
        return ResponseEntity.ok().eTag(etag(updated.version())).body(updated);
    }

    static String etag(long version) {
        return ETags.of(version);
    }
}
