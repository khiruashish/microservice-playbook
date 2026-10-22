package dev.foodfleet.orders.domain;

public final class Pricing {

    private final Menu menu;

    public Pricing(Menu menu) {
        this.menu = menu;
    }

    /** Items plus 5% GST, in paise. */
    public long total(Order order) {
        long items = 0;
        for (OrderLine line : order.lines()) {
            items += menu.priceInPaise(line.item()) * line.quantity();
        }
        return items + Math.round(items * 0.05);
    }
}
