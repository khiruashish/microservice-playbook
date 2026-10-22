package dev.foodfleet.ch18.app;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class TrackingController {

    private final EtaService etas;

    TrackingController(EtaService etas) {
        this.etas = etas;
    }

    /** Never depends on maps: must stay up whatever maps does (the bulkhead's job). */
    @GetMapping("/orders/{orderId}/status")
    String status(@PathVariable String orderId) {
        return "{\"orderId\":\"" + orderId + "\",\"status\":\"OUT_FOR_DELIVERY\"}";
    }

    @GetMapping("/orders/{orderId}/eta")
    EtaService.Eta eta(@PathVariable String orderId, @RequestParam String rider, @RequestParam String customer) {
        return etas.eta(orderId, rider, customer);
    }
}
