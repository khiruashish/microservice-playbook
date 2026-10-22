package dev.foodfleet.ch65.sample.domain.money;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Rupees, two decimals. */
public record Money(BigDecimal amount) implements Comparable<Money> {

    public static final Money ZERO = of("0.00");

    public static Money of(String amount) {
        return new Money(new BigDecimal(amount).setScale(2, RoundingMode.HALF_EVEN));
    }

    public Money minus(Money other) {
        return new Money(amount.subtract(other.amount));
    }

    public Money percent(int p) {
        return new Money(amount.multiply(BigDecimal.valueOf(p)).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_EVEN));
    }

    @Override
    public int compareTo(Money o) {
        return amount.compareTo(o.amount);
    }

    @Override
    public String toString() {
        return amount.toPlainString();
    }
}
