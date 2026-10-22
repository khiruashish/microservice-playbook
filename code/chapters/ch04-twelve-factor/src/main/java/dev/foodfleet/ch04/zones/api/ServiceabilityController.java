package dev.foodfleet.ch04.zones.api;

import dev.foodfleet.ch04.zones.zone.ServiceabilityService;
import dev.foodfleet.ch04.zones.zone.ServiceabilityService.Serviceability;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Factor 13 (API first): the contract is designed and documented before the implementation. */
@RestController
@RequestMapping("/api/v1/serviceability")
class ServiceabilityController {

    private final ServiceabilityService serviceability;

    ServiceabilityController(ServiceabilityService serviceability) {
        this.serviceability = serviceability;
    }

    @GetMapping("/{pincode}")
    Serviceability check(@PathVariable @Pattern(regexp = "[1-9][0-9]{5}") String pincode) {
        return serviceability.check(pincode);
    }
}
