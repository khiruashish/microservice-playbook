package dev.foodfleet.ch02.menu.pricing;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param maxStaleness how old a last-known surge value may be and still be used during a
 *                     pricing-service outage. This is a business decision (revenue vs fairness),
 *                     so it lives in configuration, not code.
 */
@ConfigurationProperties("foodfleet.pricing")
public record PricingProperties(@DefaultValue("5m") Duration maxStaleness) {
}
