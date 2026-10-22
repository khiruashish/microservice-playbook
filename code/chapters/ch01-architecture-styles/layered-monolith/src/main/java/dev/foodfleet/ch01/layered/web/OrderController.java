package dev.foodfleet.ch01.layered.web;

import dev.foodfleet.ch01.layered.domain.Order;
import dev.foodfleet.ch01.layered.service.KitchenService;
import dev.foodfleet.ch01.layered.service.OrderService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class OrderController {

    private final OrderService orderService;
    private final KitchenService kitchenService;

    public OrderController(OrderService orderService, KitchenService kitchenService) {
        this.orderService = orderService;
        this.kitchenService = kitchenService;
    }

    @PostMapping("/orders")
    public ResponseEntity<OrderResponse> placeOrder(@Valid @RequestBody PlaceOrderRequest request) {
        Order order = orderService.placeOrder(request.customerId(), request.restaurantId(), request.domainLines());
        return ResponseEntity.created(URI.create("/api/orders/" + order.id())).body(OrderResponse.from(order));
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable UUID orderId) {
        return ResponseEntity.of(orderService.findOrder(orderId).map(OrderResponse::from));
    }

    /** Operational endpoint used by the demo: simulates a restaurant pausing orders. */
    @PostMapping("/kitchens/{restaurantId}/pause")
    public ResponseEntity<Void> pauseKitchen(@PathVariable String restaurantId) {
        kitchenService.pause(restaurantId);
        return ResponseEntity.noContent().build();
    }
}
