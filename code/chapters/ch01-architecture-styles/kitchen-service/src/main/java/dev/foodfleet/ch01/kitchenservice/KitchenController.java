package dev.foodfleet.ch01.kitchenservice;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/kitchens/{restaurantId}")
class KitchenController {

    private final KitchenAvailabilityService kitchen;

    KitchenController(KitchenAvailabilityService kitchen) {
        this.kitchen = kitchen;
    }

    @GetMapping("/availability")
    AvailabilityResponse availability(@PathVariable String restaurantId) {
        requireKnown(restaurantId);
        return kitchen.availability(restaurantId);
    }

    @PostMapping("/pause")
    ResponseEntity<Void> pause(@PathVariable String restaurantId) {
        requireKnown(restaurantId);
        kitchen.pause(restaurantId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/resume")
    ResponseEntity<Void> resume(@PathVariable String restaurantId) {
        requireKnown(restaurantId);
        kitchen.resume(restaurantId);
        return ResponseEntity.noContent().build();
    }

    private void requireKnown(String restaurantId) {
        if (!kitchen.isKnown(restaurantId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown restaurant " + restaurantId);
        }
    }
}
