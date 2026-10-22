package dev.foodfleet.lab07;

/** A message that will never be processable as sent: retrying it is pointless, so it goes straight to the DLT. */
public class InvalidEvent extends RuntimeException {

    public InvalidEvent(String message, Throwable cause) {
        super(message, cause);
    }
}
