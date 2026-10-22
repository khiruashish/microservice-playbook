package dev.foodfleet.ch28.payments;

/** The body of {@code POST /payments}. */
public record PaymentRequest(String orderId, long amountPaise) {

    /** Canonical form for the request fingerprint: same request, same fingerprint. */
    public String canonical() {
        return "orderId=" + orderId + ";amountPaise=" + amountPaise;
    }
}
