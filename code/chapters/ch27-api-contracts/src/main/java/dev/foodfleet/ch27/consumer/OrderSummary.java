package dev.foodfleet.ch27.consumer;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

/**
 * How a consumer (notification-service) reads the Order API as a tolerant reader: only the fields
 * it uses, and an UNKNOWN status for values added after it was built. Unknown fields are ignored
 * by Jackson 3's defaults; unknown enum values need the EnumFeature below (or the Boot property in
 * application.yml).
 */
public record OrderSummary(String id, Status status, Integer etaMinutes) {

    public enum Status {
        PLACED, ACCEPTED, PREPARING, PICKED_UP, DELIVERED, CANCELLED,
        @JsonEnumDefaultValue UNKNOWN;

        /** Business logic must give UNKNOWN a sensible meaning: here, "still in progress". */
        public boolean finished() {
            return this == DELIVERED || this == CANCELLED;
        }
    }
}
