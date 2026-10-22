package dev.foodfleet.lab08;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderController {

    private final Orders orders;
    private final MenuClient menus;

    public OrderController(Orders orders, MenuClient menus) {
        this.orders = orders;
        this.menus = menus;
    }

    @GetMapping("/orders/{id}")
    @PreAuthorize("@orderAccess.canRead(authentication, #id)")
    public Orders.Order order(@PathVariable String id) {
        return orders.find(id).orElseThrow(() -> new AccessDeniedException(id));
    }

    /** Calls menu-service with the caller's token (see TokenRelay): menu-service decides for itself. */
    @GetMapping("/orders/{id}/menu")
    @PreAuthorize("@orderAccess.canRead(authentication, #id)")
    public MenuClient.Menu menuForOrder(@PathVariable String id) {
        return menus.menu(orders.find(id).orElseThrow().restaurantId());
    }

    /** Another customer's order and a missing one look the same: 404, so ids can't be probed (ADR-035). */
    @ExceptionHandler
    ProblemDetail denied(AccessDeniedException e) {
        return ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
    }
}
