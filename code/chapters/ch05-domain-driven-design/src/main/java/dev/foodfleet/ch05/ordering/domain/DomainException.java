package dev.foodfleet.ch05.ordering.domain;

/** A business rule was violated. The message is phrased in the ubiquitous language, for humans. */
public class DomainException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DomainException(String message) {
        super(message);
    }
}
