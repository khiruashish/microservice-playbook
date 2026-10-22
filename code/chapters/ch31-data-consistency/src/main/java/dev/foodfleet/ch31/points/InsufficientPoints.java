package dev.foodfleet.ch31.points;

public class InsufficientPoints extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InsufficientPoints(long customerId, int wanted) {
        super("customer " + customerId + " has fewer than " + wanted + " points");
    }
}
