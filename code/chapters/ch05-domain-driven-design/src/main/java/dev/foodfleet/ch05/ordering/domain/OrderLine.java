package dev.foodfleet.ch05.ordering.domain;

import dev.foodfleet.ch05.ordering.domain.Identifiers.MenuItemId;
import java.util.Objects;

/**
 * Entity <em>inside</em> the Order aggregate. Its identity (the menu item) only means something
 * within one order, and nothing outside the aggregate may hold a reference to it or change it.
 * The price is a <b>snapshot</b>: if the restaurant changes the menu price later, this order keeps
 * the price the customer saw.
 */
public final class OrderLine {

    private final MenuItemId menuItemId;
    private final String name;
    private final Money unitPrice;
    private Quantity quantity;

    OrderLine(MenuItemId menuItemId, String name, Money unitPrice, Quantity quantity) {
        this.menuItemId = Objects.requireNonNull(menuItemId);
        this.name = Objects.requireNonNull(name);
        this.unitPrice = Objects.requireNonNull(unitPrice);
        this.quantity = Objects.requireNonNull(quantity);
    }

    void increaseBy(Quantity more) {
        quantity = quantity.plus(more);
    }

    public MenuItemId menuItemId() {
        return menuItemId;
    }

    public String name() {
        return name;
    }

    public Money unitPrice() {
        return unitPrice;
    }

    public Quantity quantity() {
        return quantity;
    }

    public Money lineTotal() {
        return unitPrice.times(quantity.value());
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof OrderLine other && menuItemId.equals(other.menuItemId);
    }

    @Override
    public int hashCode() {
        return menuItemId.hashCode();
    }

    @Override
    public String toString() {
        return quantity.value() + " × " + name + " @ " + unitPrice;
    }
}
