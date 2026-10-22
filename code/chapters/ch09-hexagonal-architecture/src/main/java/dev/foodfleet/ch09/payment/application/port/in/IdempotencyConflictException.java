package dev.foodfleet.ch09.payment.application.port.in;

/** The same Idempotency-Key was reused for a different request (chapter 3). Maps to 409. */
public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException(String key) {
        super("Idempotency-Key " + key + " was already used for a different payment");
    }
}
