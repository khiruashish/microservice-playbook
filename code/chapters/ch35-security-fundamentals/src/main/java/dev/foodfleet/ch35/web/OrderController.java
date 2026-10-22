package dev.foodfleet.ch35.web;

import dev.foodfleet.ch35.identity.Caller;
import dev.foodfleet.ch35.orders.OrderPatch;
import dev.foodfleet.ch35.orders.OrderService;
import dev.foodfleet.ch35.orders.OrderView;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Thin on purpose: the caller comes from authentication, the checks live in {@link OrderService}, requests bind
 * to {@link OrderPatch} (never to the stored order) and responses are {@link OrderView}s.
 */
@RestController
public class OrderController {

    private final OrderService orders;

    public OrderController(OrderService orders) {
        this.orders = orders;
    }

    @GetMapping("/orders/{id}")
    public OrderView get(@RequestAttribute(TokenAuthenticationFilter.CALLER) Caller caller, @PathVariable long id) {
        return orders.get(caller, id);
    }

    @PatchMapping("/orders/{id}")
    public OrderView update(@RequestAttribute(TokenAuthenticationFilter.CALLER) Caller caller, @PathVariable long id,
                            @RequestBody OrderPatch patch) {
        return orders.update(caller, id, patch);
    }

    @PostMapping("/orders/{id}/refund")
    public OrderView refund(@RequestAttribute(TokenAuthenticationFilter.CALLER) Caller caller, @PathVariable long id) {
        return orders.refund(caller, id);
    }

    @GetMapping("/orders")
    public List<OrderView> mine(@RequestAttribute(TokenAuthenticationFilter.CALLER) Caller caller,
                                @RequestParam(required = false) Integer page,
                                @RequestParam(required = false) Integer size) {
        return orders.list(caller, page, size);
    }

    @GetMapping("/admin/orders/export")
    public List<OrderView> export(@RequestAttribute(TokenAuthenticationFilter.CALLER) Caller caller) {
        return orders.exportAll(caller);
    }
}
