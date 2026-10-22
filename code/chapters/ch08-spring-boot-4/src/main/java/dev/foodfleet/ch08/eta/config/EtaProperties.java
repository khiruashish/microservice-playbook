package dev.foodfleet.ch08.eta.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param fallbackTravelMinutes zone-average travel time used when dispatch can't answer
 * @param budget                total time an ETA request may spend waiting for downstream services
 */
@ConfigurationProperties("foodfleet.eta")
public record EtaProperties(@DefaultValue("20") int fallbackTravelMinutes, @DefaultValue("800ms") Duration budget) {
}
