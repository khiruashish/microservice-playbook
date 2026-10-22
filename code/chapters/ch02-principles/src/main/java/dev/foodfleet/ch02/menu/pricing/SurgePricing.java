package dev.foodfleet.ch02.menu.pricing;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Design for failure, layer 2: <b>graceful degradation</b>. When pricing-service is down, customers
 * can still browse and order. The menu shows the last known surge (if it's recent) or the base price,
 * and says which one it used. Failing the whole menu because an optional enrichment failed is the
 * most common resilience mistake in microservices.
 *
 * <p>The last-known values live in this instance's memory. That is acceptable here: each pod
 * warms up independently and the data is short-lived and non-critical. It is not acceptable for
 * anything that must be consistent across replicas (chapter 68).
 */
@Service
public class SurgePricing {

    private static final Logger log = LoggerFactory.getLogger(SurgePricing.class);

    private final SurgeLookup lookup;
    private final Clock clock;
    private final Duration maxStaleness;
    private final Map<String, LastKnown> lastKnown = new ConcurrentHashMap<>();

    public SurgePricing(SurgeLookup lookup, Clock clock, PricingProperties properties) {
        this.lookup = lookup;
        this.clock = clock;
        this.maxStaleness = properties.maxStaleness();
    }

    public SurgeQuote quote(String zone) {
        try {
            int bps = lookup.multiplierBps(zone);
            lastKnown.put(zone, new LastKnown(bps, clock.instant()));
            return new SurgeQuote(zone, bps, PriceSource.LIVE);
        } catch (RuntimeException failure) {
            return degrade(zone, failure);
        }
    }

    private SurgeQuote degrade(String zone, RuntimeException failure) {
        LastKnown known = lastKnown.get(zone);
        if (known != null && Duration.between(known.at(), clock.instant()).compareTo(maxStaleness) <= 0) {
            log.warn("pricing.degraded zone={} source=LAST_KNOWN cause={}", zone, failure.toString());
            return new SurgeQuote(zone, known.bps(), PriceSource.LAST_KNOWN);
        }
        log.warn("pricing.degraded zone={} source=BASE cause={}", zone, failure.toString());
        return new SurgeQuote(zone, SurgeQuote.BASE_BPS, PriceSource.BASE);
    }

    private record LastKnown(int bps, Instant at) {
    }
}
