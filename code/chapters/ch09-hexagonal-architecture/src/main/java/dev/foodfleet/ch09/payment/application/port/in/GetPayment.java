package dev.foodfleet.ch09.payment.application.port.in;

import dev.foodfleet.ch09.payment.domain.PaymentId;

public interface GetPayment {

    PaymentView get(PaymentId paymentId);
}
