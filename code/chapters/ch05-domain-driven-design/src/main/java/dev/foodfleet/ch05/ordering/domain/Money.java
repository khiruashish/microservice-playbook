package dev.foodfleet.ch05.ordering.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Value object: Indian rupees held as whole paise. Immutable, compared by value, and it validates
 * itself, so an invalid amount can't exist anywhere in the domain.
 */
public record Money(long paise) implements Comparable<Money> {

    public static final Money ZERO = new Money(0);

    public Money {
        if (paise < 0) {
            throw new IllegalArgumentException("Money cannot be negative: " + paise + " paise");
        }
    }

    public static Money ofPaise(long paise) {
        return new Money(paise);
    }

    /** Parses "349" or "349.50". More than two decimal places is rejected, never silently rounded. */
    public static Money ofRupees(String rupees) {
        try {
            return new Money(new BigDecimal(rupees.strip()).movePointRight(2).longValueExact());
        } catch (ArithmeticException | NumberFormatException e) {
            throw new IllegalArgumentException("Not a valid rupee amount: '" + rupees + "'", e);
        }
    }

    public Money plus(Money other) {
        return new Money(Math.addExact(paise, other.paise));
    }

    public Money times(int factor) {
        return new Money(Math.multiplyExact(paise, factor));
    }

    /** Percentage in basis points (500 = 5%), rounded half-up to the nearest paisa. */
    public Money percentage(int basisPoints) {
        return new Money(Math.addExact(Math.multiplyExact(paise, basisPoints), 5_000) / 10_000);
    }

    public boolean isLessThan(Money other) {
        return paise < other.paise;
    }

    @Override
    public int compareTo(Money other) {
        return Long.compare(paise, other.paise);
    }

    @Override
    public String toString() {
        return "₹" + BigDecimal.valueOf(paise).movePointLeft(2).setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }
}
