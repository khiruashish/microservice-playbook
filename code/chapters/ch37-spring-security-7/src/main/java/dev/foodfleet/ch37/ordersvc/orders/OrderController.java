package dev.foodfleet.ch37.ordersvc.orders;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderController {

    private final OrderService orders;

    public OrderController(OrderService orders) {
        this.orders = orders;
    }

    @GetMapping("/orders/{id}")
    public ResponseEntity<Order> get(@PathVariable long id) {
        try {
            return ResponseEntity.ok(orders.get(id));
        } catch (AuthorizationDeniedException e) {
            return ResponseEntity.notFound().build();                 // don't confirm that someone else's order exists
        }
    }

    @PostMapping("/orders/{id}/refund")
    public Order refund(@PathVariable long id) {
        return orders.refund(id);
    }
}
