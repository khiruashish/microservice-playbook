package dev.foodfleet.ch09.payment.application.port.in;

import dev.foodfleet.ch09.payment.domain.PaymentId;

/** Driving port: capture an authorized payment once the order is on its way. */
public interface CapturePayment {

    PaymentView capture(PaymentId paymentId);
}
