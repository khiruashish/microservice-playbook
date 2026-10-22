package dev.foodfleet.ch27.api;

import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /orders/{id}}: 200 with an {@link OrderView}, or 404 with a problem document. Both
 * outcomes are part of the contract: consumers' contracts pin them down (see
 * {@code src/test/resources/contracts}).
 */
@RestController
public class OrderController {

    private final Map<String, OrderView> orders = Map.of("o-9001", new OrderView("o-9001", OrderView.Status.PICKED_UP,
            17, "Suresh K", 64_000, Instant.parse("2026-10-06T13:02:11Z"),
            new OrderView.Restaurant("r-11", "Meghana Foods")));

    @GetMapping("/orders/{id}")
    public ResponseEntity<?> order(@PathVariable String id) {
        OrderView order = orders.get(id);
        if (order == null) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "No order " + id);
            problem.setTitle("Order not found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
        }
        return ResponseEntity.ok(order);
    }
}
