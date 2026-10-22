package dev.foodfleet.ch01.modulith.kitchen.internal;

import dev.foodfleet.ch01.modulith.kitchen.KitchenAvailability;
import dev.foodfleet.ch01.modulith.kitchen.KitchenOperations;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes exactly the same HTTP contract as the extracted kitchen-service. When the kitchen moves
 * out, the gateway route changes and callers don't notice.
 */
@RestController
@RequestMapping("/api/v1/kitchens/{restaurantId}")
class KitchenController {

    private final KitchenOperations kitchen;

    KitchenController(KitchenOperations kitchen) {
        this.kitchen = kitchen;
    }

    @GetMapping("/availability")
    KitchenAvailability availability(@PathVariable String restaurantId) {
        return kitchen.availability(restaurantId);
    }

    @PostMapping("/pause")
    ResponseEntity<Void> pause(@PathVariable String restaurantId) {
        kitchen.pause(restaurantId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/resume")
    ResponseEntity<Void> resume(@PathVariable String restaurantId) {
        kitchen.resume(restaurantId);
        return ResponseEntity.noContent().build();
    }
}
