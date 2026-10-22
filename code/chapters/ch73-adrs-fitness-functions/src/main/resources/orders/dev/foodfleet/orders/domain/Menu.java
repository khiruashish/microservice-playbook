package dev.foodfleet.orders.domain;

/** A port: prices come from the menu, wherever it lives. */
public interface Menu {
    long priceInPaise(String item);
}
