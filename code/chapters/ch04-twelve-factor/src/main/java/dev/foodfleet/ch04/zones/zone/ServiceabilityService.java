package dev.foodfleet.ch04.zones.zone;

import dev.foodfleet.ch04.zones.config.ZoneProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * VI. Processes: stateless. Every request reads what it needs from the backing service, so any
 * replica can serve any request and replicas can be added or killed at will (VIII, IX).
 *
 * <p>Factor 14 (telemetry): a business metric, not only technical ones. "How many checkouts hit
 * an unserviceable pincode?" is a product question the dashboard should answer.
 */
@Service
public class ServiceabilityService {

    private static final Logger log = LoggerFactory.getLogger(ServiceabilityService.class);

    private final ZoneStore zones;
    private final ZoneProperties properties;
    private final Counter serviceable;
    private final Counter notServiceable;
    private final Counter unknown;

    public ServiceabilityService(ZoneStore zones, ZoneProperties properties, MeterRegistry registry) {
        this.zones = zones;
        this.properties = properties;
        this.serviceable = checks(registry, "serviceable");
        this.notServiceable = checks(registry, "not_serviceable");
        this.unknown = checks(registry, "unknown_pincode");
    }

    public Serviceability check(String pincode) {
        return zones.find(pincode)
                .map(zone -> {
                    (zone.serviceable() ? serviceable : notServiceable).increment();
                    long fee = zone.deliveryFeePaise() != null ? zone.deliveryFeePaise() : properties.defaultDeliveryFeePaise();
                    return new Serviceability(pincode, zone.areaName(), properties.city(), zone.serviceable(),
                            zone.serviceable() ? fee : null);
                })
                .orElseGet(() -> {
                    unknown.increment();
                    // XI. Logs: an event on stdout, with key=value fields that structured logging turns into JSON
                    log.info("zone.unknown pincode={} city={}", pincode, properties.city());
                    return new Serviceability(pincode, null, properties.city(), false, null);
                });
    }

    private static Counter checks(MeterRegistry registry, String result) {
        return Counter.builder("foodfleet.serviceability.checks")
                .description("Serviceability checks by outcome")
                .tag("result", result)
                .register(registry);
    }

    /** @param deliveryFeePaise null when not serviceable */
    public record Serviceability(String pincode, String areaName, String city, boolean serviceable,
                                 Long deliveryFeePaise) {
    }
}
