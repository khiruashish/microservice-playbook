package dev.foodfleet.ch01.modulith.order.internal;

import dev.foodfleet.ch01.modulith.order.OrderManagement;
import dev.foodfleet.ch01.modulith.order.OrderSummary;
import dev.foodfleet.ch01.modulith.order.PlaceOrder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
class OrderController {

    private final OrderManagement orders;

    OrderController(OrderManagement orders) {
        this.orders = orders;
    }

    @PostMapping
    ResponseEntity<OrderSummary> placeOrder(@Valid @RequestBody PlaceOrderRequest request) {
        OrderSummary summary = orders.placeOrder(request.toCommand());
        return ResponseEntity.created(URI.create("/api/v1/orders/" + summary.orderId())).body(summary);
    }

    @GetMapping("/{orderId}")
    ResponseEntity<OrderSummary> getOrder(@PathVariable UUID orderId) {
        return ResponseEntity.of(orders.findOrder(orderId));
    }

    /** HTTP request shape, kept separate from the module's command so the API can evolve independently. */
    record PlaceOrderRequest(
            @NotBlank String customerId,
            @NotBlank String restaurantId,
            @NotEmpty @Size(max = Order.MAX_LINES) List<@Valid Line> lines) {

        record Line(@NotBlank String itemId, @NotBlank String name,
                    @Min(1) int quantity, @PositiveOrZero long unitPricePaise) {
        }

        PlaceOrder toCommand() {
            return new PlaceOrder(customerId, restaurantId, lines.stream()
                    .map(l -> new PlaceOrder.Line(l.itemId(), l.name(), l.quantity(), l.unitPricePaise()))
                    .toList());
        }
    }
}
