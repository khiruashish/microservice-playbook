package dev.foodfleet.ch09.payment.application.port.in;

import dev.foodfleet.ch09.payment.domain.Money;
import dev.foodfleet.ch09.payment.domain.PaymentMethod;

/** Driving port: "take the customer's money for this order", safely repeatable with the same key. */
public interface AuthorizePayment {

    PaymentView authorize(Command command);

    record Command(String orderId, Money amount, PaymentMethod method, String idempotencyKey) {
    }
}
