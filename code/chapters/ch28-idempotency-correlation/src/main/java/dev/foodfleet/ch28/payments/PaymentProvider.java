package dev.foodfleet.ch28.payments;

/**
 * The external payment provider. Our idempotency key is passed on as the provider's idempotency
 * key, so a retry after an unknown outcome (a timeout) can't charge twice: the provider returns the
 * original result. Providers such as Stripe and Razorpay accept such keys.
 */
public interface PaymentProvider {

    /** Charges the order once per {@code idempotencyKey}; returns the provider's reference. */
    String charge(String idempotencyKey, PaymentRequest request);

    /** The provider rejected the call before doing anything (e.g. 503): safe to try again. */
    class Unavailable extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public Unavailable(String message) {
            super(message);
        }
    }

    /** No answer in time: the charge may or may not have happened. */
    class OutcomeUnknown extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public OutcomeUnknown(String message) {
            super(message);
        }
    }
}
