package dev.foodfleet.ch09.payment.domain;

import java.util.Objects;
import java.util.UUID;

public record PaymentId(String value) {

    public PaymentId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("PaymentId must not be blank");
        }
    }

    public static PaymentId newId() {
        return new PaymentId("pay_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
    }

    @Override
    public String toString() {
        return value;
    }
}
