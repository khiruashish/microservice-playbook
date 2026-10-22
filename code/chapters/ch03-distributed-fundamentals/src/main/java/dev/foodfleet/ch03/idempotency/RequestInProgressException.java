package dev.foodfleet.ch03.idempotency;

/** A request with this Idempotency-Key is still being processed. Maps to HTTP 409; the client retries later. */
public class RequestInProgressException extends RuntimeException {

    public RequestInProgressException(String key) {
        super("A request with Idempotency-Key " + key + " is still in progress");
    }
}
