package dev.foodfleet.ch05.ordering.domain;

/** Value object: how many of one dish. The range is a business rule, so it lives in the type. */
public record Quantity(int value) {

    public static final int MAX_PER_LINE = 20;

    public Quantity {
        if (value < 1 || value > MAX_PER_LINE) {
            throw new IllegalArgumentException("Quantity must be between 1 and " + MAX_PER_LINE + ", was " + value);
        }
    }

    public static Quantity of(int value) {
        return new Quantity(value);
    }

    public Quantity plus(Quantity other) {
        return new Quantity(value + other.value); // re-validates: 15 + 10 is rejected
    }
}
