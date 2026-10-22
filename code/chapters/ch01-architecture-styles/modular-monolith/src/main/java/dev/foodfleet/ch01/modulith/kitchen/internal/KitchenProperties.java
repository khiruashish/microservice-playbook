package dev.foodfleet.ch01.modulith.kitchen.internal;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Type-safe kitchen configuration ({@code foodfleet.kitchen.*}).
 *
 * @param mode              {@code in-process} (kitchen module answers) or {@code remote} (kitchen-service answers)
 * @param maxActiveTickets  back-pressure: a kitchen with this many open tickets stops accepting orders
 */
@Validated
@ConfigurationProperties("foodfleet.kitchen")
public record KitchenProperties(
        @DefaultValue("in-process") String mode,
        @DefaultValue("20") @Min(1) int maxActiveTickets) {
}
