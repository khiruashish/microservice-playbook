package dev.foodfleet.ch25.kafka;

/** The event can never be processed (bad data): retrying is pointless, it goes to the DLT at once. */
public class InvalidEventException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidEventException(String message) {
        super(message);
    }
}
