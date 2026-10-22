package dev.foodfleet.ch08.eta.client;

/** Without the kitchen's prep estimate there is no honest ETA, so this fails the request (503). */
public class KitchenUnavailableException extends RuntimeException {

    public KitchenUnavailableException(String orderId, Throwable cause) {
        super("Kitchen estimate unavailable for order " + orderId, cause);
    }
}
