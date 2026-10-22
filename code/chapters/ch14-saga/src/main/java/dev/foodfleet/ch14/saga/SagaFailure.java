package dev.foodfleet.ch14.saga;

/**
 * Participants signal failures with these two types, because the saga reacts differently:
 * a business rejection is final (compensate); a transient failure may succeed on retry.
 */
public sealed class SagaFailure extends RuntimeException permits SagaFailure.BusinessRejection, SagaFailure.Transient {

    SagaFailure(String message) {
        super(message);
    }

    /** "No": card declined, coupon exhausted, restaurant closed. Retrying won't help. */
    public static final class BusinessRejection extends SagaFailure {
        public BusinessRejection(String message) {
            super(message);
        }
    }

    /** "Not now": timeout, 503, broker unavailable. The participant may or may not have acted. */
    public static final class Transient extends SagaFailure {
        public Transient(String message) {
            super(message);
        }
    }
}
