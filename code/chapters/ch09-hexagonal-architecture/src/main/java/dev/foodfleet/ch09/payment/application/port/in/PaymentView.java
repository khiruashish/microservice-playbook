package dev.foodfleet.ch09.payment.application.port.in;

import dev.foodfleet.ch09.payment.domain.Payment;
import dev.foodfleet.ch09.payment.domain.PaymentStatus;

/**
 * What use cases return: an immutable snapshot, so driving adapters can't change the aggregate
 * behind the application service's back.
 */
public record PaymentView(String paymentId, String orderId, long amountPaise, PaymentStatus status,
                          long refundedPaise, String declineReason) {

    public static PaymentView of(Payment p) {
        return new PaymentView(p.id().value(), p.orderId(), p.amount().paise(), p.status(),
                p.refunded().paise(), p.declineReason());
    }
}
