package dev.foodfleet.lab12;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class OrderController {

    private final PlaceOrder placeOrder;

    OrderController(PlaceOrder placeOrder) {
        this.placeOrder = placeOrder;
    }

    @PostMapping("/orders")
    ResponseEntity<Void> place(@RequestBody PlaceOrder.Request request) {
        placeOrder.place(request);
        return ResponseEntity.accepted().build();
    }
}
