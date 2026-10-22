package dev.foodfleet.ch05.ordering.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Typed identifiers. A method taking {@code (CustomerId, RestaurantId)} can't be called with the
 * arguments swapped, which a pair of Strings or UUIDs would allow. Grouped in one file to keep the
 * chapter readable; production code usually gives each its own file.
 */
public final class Identifiers {

    private Identifiers() {
    }

    public record OrderId(UUID value) {
        public OrderId {
            Objects.requireNonNull(value, "value");
        }

        public static OrderId newId() {
            return new OrderId(UUID.randomUUID());
        }

        public static OrderId of(String value) {
            return new OrderId(UUID.fromString(value));
        }
    }

    public record CustomerId(String value) {
        public CustomerId {
            requireText(value, "CustomerId");
        }
    }

    public record RestaurantId(String value) {
        public RestaurantId {
            requireText(value, "RestaurantId");
        }
    }

    public record MenuItemId(String value) {
        public MenuItemId {
            requireText(value, "MenuItemId");
        }
    }

    private static void requireText(String value, String type) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(type + " must not be blank");
        }
    }
}
