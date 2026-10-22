package dev.foodfleet.ch09.payment.domain;

/** Indian rupees in paise. Never a double. */
public record Money(long paise) implements Comparable<Money> {

    public static final Money ZERO = new Money(0);

    public Money {
        if (paise < 0) {
            throw new IllegalArgumentException("Money cannot be negative: " + paise);
        }
    }

    public static Money ofPaise(long paise) {
        return new Money(paise);
    }

    public Money plus(Money other) {
        return new Money(Math.addExact(paise, other.paise));
    }

    public Money minus(Money other) {
        return new Money(paise - other.paise);
    }

    public boolean isZero() {
        return paise == 0;
    }

    @Override
    public int compareTo(Money other) {
        return Long.compare(paise, other.paise);
    }

    @Override
    public String toString() {
        return String.format("₹%d.%02d", paise / 100, paise % 100);
    }
}
