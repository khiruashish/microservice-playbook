package dev.foodfleet.ch04.zones.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * III. Config: everything that varies between deploys lives in the environment, bound once into a
 * typed, validated object. In Kubernetes the values arrive as environment variables
 * ({@code FOODFLEET_ZONES_CITY=Bengaluru}) or mounted files, never as code edits.
 *
 * <p>No defaults on purpose: a missing city or fee is a deployment mistake, and the service
 * <b>fails at startup</b> instead of serving wrong prices for hours.
 *
 * @param city                    the city this deployment serves (one deployment per city cell)
 * @param defaultDeliveryFeePaise fee for serviceable pincodes without a zone-specific fee
 * @param maxDeliveryKm           delivery radius limit, enforced by delivery-service; reported here
 */
@Validated
@ConfigurationProperties("foodfleet.zones")
public record ZoneProperties(
        @NotBlank String city,
        @Min(0) @Max(50_000) long defaultDeliveryFeePaise,
        @Min(1) @Max(30) int maxDeliveryKm) {
}
