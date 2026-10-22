package dev.foodfleet.ch05.ordering.api;

import dev.foodfleet.ch05.ordering.application.OrderApplicationService;
import dev.foodfleet.ch05.ordering.application.OrderNotFoundException;
import dev.foodfleet.ch05.ordering.application.PlaceOrderCommand;
import dev.foodfleet.ch05.ordering.domain.CancellationReason;
import dev.foodfleet.ch05.ordering.domain.DeliveryAddress;
import dev.foodfleet.ch05.ordering.domain.Identifiers.CustomerId;
import dev.foodfleet.ch05.ordering.domain.Identifiers.MenuItemId;
import dev.foodfleet.ch05.ordering.domain.Identifiers.OrderId;
import dev.foodfleet.ch05.ordering.domain.Identifiers.RestaurantId;
import dev.foodfleet.ch05.ordering.domain.Order;
import dev.foodfleet.ch05.ordering.domain.Quantity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST adapter: translates HTTP JSON into commands in domain types and the aggregate back into a
 * response. Shape validation (required fields) happens here; business rules stay in the domain.
 */
@RestController
@RequestMapping("/api/v1/orders")
class OrderController {

    private final OrderApplicationService orders;

    OrderController(OrderApplicationService orders) {
        this.orders = orders;
    }

    @PostMapping
    ResponseEntity<OrderResponse> place(@Valid @RequestBody PlaceOrderRequest request) {
        Order order = orders.placeOrder(request.toCommand());
        return ResponseEntity.created(URI.create("/api/v1/orders/" + order.id().value()))
                .body(OrderResponse.from(order));
    }

    @PostMapping("/{orderId}/cancel")
    OrderResponse cancel(@PathVariable String orderId, @Valid @RequestBody CancelRequest request) {
        return OrderResponse.from(orders.cancel(OrderId.of(orderId), request.reason()));
    }

    @GetMapping("/{orderId}")
    OrderResponse get(@PathVariable String orderId) {
        OrderId id = OrderId.of(orderId);
        return OrderResponse.from(orders.find(id).orElseThrow(() -> new OrderNotFoundException(id)));
    }

    record PlaceOrderRequest(@NotBlank String customerId, @NotBlank String restaurantId,
                             @NotNull @Valid Address address, @NotEmpty List<@Valid Item> items) {

        record Address(@NotBlank String line1, @NotBlank String area, @NotBlank String city,
                       @NotBlank String pincode, String landmark) {
        }

        record Item(@NotBlank String menuItemId, int quantity) {
        }

        PlaceOrderCommand toCommand() {
            return new PlaceOrderCommand(
                    new CustomerId(customerId),
                    new RestaurantId(restaurantId),
                    new DeliveryAddress(address.line1(), address.area(), address.city(), address.pincode(), address.landmark()),
                    items.stream()
                            .map(i -> new PlaceOrderCommand.Item(new MenuItemId(i.menuItemId()), Quantity.of(i.quantity())))
                            .toList());
        }
    }

    record CancelRequest(@NotNull CancellationReason reason) {
    }
}
