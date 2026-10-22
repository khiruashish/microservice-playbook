package dev.foodfleet.lab05.menu;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Typed, validated settings: a typo or an out-of-range value fails at startup, not on the first order.
 * Bound from whichever source wins: Config Server, a mounted file, an environment variable.
 */
@ConfigurationProperties("foodfleet.menu")
@Validated
public record MenuSettings(
        @Min(0) @Max(50) int discountPercent,
        @Min(1) @Max(100) int maxItemsPerOrder) {
}
