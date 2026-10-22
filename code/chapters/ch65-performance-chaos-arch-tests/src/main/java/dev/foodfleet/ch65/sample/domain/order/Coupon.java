package dev.foodfleet.ch65.sample.domain.order;

import dev.foodfleet.ch65.sample.domain.money.Money;

/** FIRST20: 20% off, at most Rs 100. */
public enum Coupon {
    FIRST20;

    public Money discount(Money subtotal) {
        Money d = subtotal.percent(20);
        return d.compareTo(Money.of("100.00")) > 0 ? Money.of("100.00") : d;
    }
}
