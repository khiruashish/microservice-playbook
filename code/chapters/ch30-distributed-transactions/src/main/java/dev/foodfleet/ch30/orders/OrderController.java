package dev.foodfleet.ch30.orders;

import java.net.URI;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderController {

    private final OrderService orders;

    public OrderController(OrderService orders) {
        this.orders = orders;
    }

    /** 202 Accepted: the order exists and will be decided shortly; the app follows the Location (or a push, chapter 26). */
    @PostMapping("/orders")
    ResponseEntity<OrderView> place(@RequestBody PlaceOrder order) {
        OrderView placed = orders.place(order);
        return ResponseEntity.accepted().location(URI.create("/orders/" + placed.id())).body(placed);
    }

    @GetMapping("/orders/{id}")
    OrderView get(@PathVariable long id) {
        return orders.find(id).orElseThrow(() -> new NoSuchElementException("no order " + id));
    }

    @ExceptionHandler(NoSuchElementException.class)
    ProblemDetail notFound(NoSuchElementException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badRequest(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
