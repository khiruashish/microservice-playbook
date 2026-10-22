package dev.foodfleet.ch09.payment.application.port.in;

public class PaymentNotFoundException extends RuntimeException {

    public PaymentNotFoundException(String paymentId) {
        super("No payment " + paymentId);
    }
}
