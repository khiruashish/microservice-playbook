package dev.foodfleet.ch25.kafka;

/**
 * The restaurant-side tablet API (a partner system). Calls can fail while it is down; those
 * failures are worth retrying.
 */
public interface KitchenDisplay {

    void show(OrderEvent event);

    /** A temporary failure: timeout, 503, connection refused. */
    class Unavailable extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public Unavailable(String message) {
            super(message);
        }
    }
}
