package dev.foodfleet.ch16.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Where each capability lives today. The migration's progress is visible in this one object, and
 * moving a capability, or moving it back, is a config change.
 */
@ConfigurationProperties("foodfleet.strangler")
public record StranglerProperties(String monolith, String pricingService, String orderHistoryService,
                                  @DefaultValue("0") int pricingCanaryPercent) {
}
