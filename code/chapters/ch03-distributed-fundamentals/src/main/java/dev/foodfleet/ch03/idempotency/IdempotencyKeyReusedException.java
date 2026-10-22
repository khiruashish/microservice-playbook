package dev.foodfleet.ch03.idempotency;

/** The same Idempotency-Key was sent with a different request body. A client bug; maps to HTTP 422. */
public class IdempotencyKeyReusedException extends RuntimeException {

    public IdempotencyKeyReusedException(String key) {
        super("Idempotency-Key " + key + " was already used for a different request");
    }
}
