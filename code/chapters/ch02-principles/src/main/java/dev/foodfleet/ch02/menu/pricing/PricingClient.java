package dev.foodfleet.ch02.menu.pricing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/** Declarative client for pricing-service (owned by another team). */
@HttpExchange("/api/v1/surge")
public interface PricingClient {

    @GetExchange("/{zone}")
    SurgeResponse currentSurge(@PathVariable String zone);

    /**
     * <strong>Tolerant reader:</strong> we bind only the fields we use and ignore everything else.
     * pricing-service can add fields (reason, currency, campaignId...) without coordinating a release
     * with us. That is what independent deployability means in practice.
     *
     * @param multiplierBps surge multiplier in basis points: 10000 = 1.0×, 12500 = 1.25×
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record SurgeResponse(String zone, int multiplierBps, Instant validUntil) {
    }
}
