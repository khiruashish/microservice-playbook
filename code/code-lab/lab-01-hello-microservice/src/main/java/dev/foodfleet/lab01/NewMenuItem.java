package dev.foodfleet.lab01;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * What a client sends to add a dish: a request type of its own, never the domain record, so the API can't be used
 * to set fields the client shouldn't (ADR-035). Bean Validation rejects bad input before it reaches the domain.
 */
public record NewMenuItem(
        @NotBlank @Size(max = 80) String name,
        @Positive long pricePaise,
        boolean vegetarian) {
}
