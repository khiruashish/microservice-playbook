package dev.foodfleet.ch09.payment.application.port.in;

import dev.foodfleet.ch09.payment.domain.Money;
import dev.foodfleet.ch09.payment.domain.PaymentId;
import java.util.Optional;

/** Driving port: give money back, partly (support agent) or entirely (kitchen rejected the order). */
public interface RefundPayment {

    PaymentView refund(PaymentId paymentId, Money amount, String reason);

    /**
     * Releases everything for an order: voids an authorization, or refunds what is left of a capture.
     * Empty if the order has no payment that holds money.
     */
    Optional<PaymentView> releaseOrder(String orderId, String reason);
}
