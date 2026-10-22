package dev.foodfleet.ch09.payment.adapter.out.gateway;

import dev.foodfleet.ch09.payment.application.port.out.PaymentGateway;
import dev.foodfleet.ch09.payment.domain.Money;
import dev.foodfleet.ch09.payment.domain.PaymentId;
import dev.foodfleet.ch09.payment.domain.PaymentMethod;

/**
 * A driven adapter for local development and demos: deterministic, no network, no account needed.
 * {@code fail@upi} is declined by the "payer", and anything above ₹10,000 by the "bank".
 */
public class FakePaymentGateway implements PaymentGateway {

    public static final String DECLINING_UPI = "fail@upi";

    @Override
    public Authorization authorize(PaymentId paymentId, Money amount, PaymentMethod method) {
        if (method.reference().equals(DECLINING_UPI)) {
            return new Declined("The payer declined the UPI request");
        }
        if (amount.compareTo(Money.ofPaise(10_000_00)) > 0) {
            return new Declined("Declined by the bank");
        }
        return new Approved("fake_" + paymentId.value());
    }

    @Override
    public void capture(String gatewayReference, Money amount) {
    }

    @Override
    public void voidAuthorization(String gatewayReference) {
    }

    @Override
    public void refund(String gatewayReference, Money amount) {
    }
}
