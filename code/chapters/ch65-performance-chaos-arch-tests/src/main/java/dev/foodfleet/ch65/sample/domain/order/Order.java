package dev.foodfleet.ch65.sample.domain.order;

import dev.foodfleet.ch65.sample.domain.money.Money;

/** An order: who, how much, how much off. */
public record Order(String customer, Money subtotal, Money discount) {

    public Money total() {
        return subtotal.minus(discount);
    }
}
