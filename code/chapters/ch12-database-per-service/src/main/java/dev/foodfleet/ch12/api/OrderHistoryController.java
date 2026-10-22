package dev.foodfleet.ch12.api;

import dev.foodfleet.ch12.composition.OrderHistory;
import dev.foodfleet.ch12.composition.OrderHistoryComposer;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customers")
class OrderHistoryController {

    private final OrderHistoryComposer composer;

    OrderHistoryController(OrderHistoryComposer composer) {
        this.composer = composer;
    }

    @GetMapping("/{customerId}/order-history")
    OrderHistory history(@PathVariable String customerId, @RequestParam(defaultValue = "20") int limit) {
        return composer.history(customerId, limit);
    }
}
