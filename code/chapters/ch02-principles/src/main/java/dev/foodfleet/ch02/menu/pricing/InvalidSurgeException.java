package dev.foodfleet.ch02.menu.pricing;

/** pricing-service answered, but with a value outside the agreed contract range. Not retried. */
public class InvalidSurgeException extends RuntimeException {

    public InvalidSurgeException(String zone, Integer multiplierBps) {
        super("Rejected surge multiplier " + multiplierBps + " bps for zone " + zone);
    }
}
