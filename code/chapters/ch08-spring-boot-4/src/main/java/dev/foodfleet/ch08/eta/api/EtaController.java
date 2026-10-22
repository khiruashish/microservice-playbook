package dev.foodfleet.ch08.eta.api;

import dev.foodfleet.ch08.eta.client.EtaService;
import dev.foodfleet.ch08.eta.domain.EtaCalculator.Breakdown;
import dev.foodfleet.ch08.eta.domain.EtaCalculator.Eta;
import dev.foodfleet.ch08.eta.domain.EtaWindow;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Two API versions on one URL, selected by the {@code API-Version} header (configured in
 * {@code spring.mvc.apiversion.*}). No version header means 1.0, so existing app installs keep
 * working; {@code "2.0+"} means "2.0 and any later minor version without its own mapping".
 */
@RestController
@RequestMapping("/api/orders/{orderId}/eta")
class EtaController {

    private final EtaService etas;

    EtaController(EtaService etas) {
        this.etas = etas;
    }

    @GetMapping(version = "1.0")
    EtaV1 v1(@PathVariable String orderId) {
        return new EtaV1(orderId, etas.etaFor(orderId).minutes());
    }

    @GetMapping(version = "2.0+")
    EtaV2 v2(@PathVariable String orderId) {
        Eta eta = etas.etaFor(orderId);
        return new EtaV2(orderId, eta.window(), eta.estimatedArrival(), eta.breakdown(), eta.degraded());
    }

    /** v1 (deprecated): a single number, as the 2023 app expects. */
    record EtaV1(String orderId, int etaMinutes) {
    }

    /** v2: a window, an absolute time, the breakdown, and an honest "degraded" flag. */
    record EtaV2(String orderId, EtaWindow window, Instant estimatedArrival, Breakdown breakdown, boolean degraded) {
    }
}
