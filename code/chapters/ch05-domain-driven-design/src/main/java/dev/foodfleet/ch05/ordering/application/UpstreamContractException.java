package dev.foodfleet.ch05.ordering.application;

/**
 * An upstream context broke the agreed contract (for example Catalog sent a malformed price).
 * An integration failure, not a business rule, so the API maps it to 502, not 422.
 */
public class UpstreamContractException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public UpstreamContractException(String message) {
        super(message);
    }
}
