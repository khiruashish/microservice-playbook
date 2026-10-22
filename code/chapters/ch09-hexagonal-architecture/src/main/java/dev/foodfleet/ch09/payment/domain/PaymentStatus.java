package dev.foodfleet.ch09.payment.domain;

/**
 * <pre>
 * PENDING ──► AUTHORIZED ──► CAPTURED ──► PARTIALLY_REFUNDED ──► REFUNDED
 *    │            │              └──────────────────────────────►┘
 *    ▼            ▼
 * DECLINED      VOIDED   (authorization released, e.g. the kitchen rejected the order)
 * </pre>
 */
public enum PaymentStatus {
    PENDING, AUTHORIZED, DECLINED, VOIDED, CAPTURED, PARTIALLY_REFUNDED, REFUNDED
}
