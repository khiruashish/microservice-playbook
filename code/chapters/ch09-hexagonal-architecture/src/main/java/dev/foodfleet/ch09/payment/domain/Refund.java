package dev.foodfleet.ch09.payment.domain;

import java.time.Instant;

public record Refund(Money amount, String reason, Instant at) {
}
