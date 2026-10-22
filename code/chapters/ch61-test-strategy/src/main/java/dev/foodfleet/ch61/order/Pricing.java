package dev.foodfleet.ch61.order;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * The money rules: subtotal, the FIRST20 coupon (20% off, at most Rs 100), 5% GST on the discounted amount, and a
 * minimum order of Rs 199 before discount. Amounts are rounded half-even to paise.
 */
public final class Pricing {

    public static final BigDecimal MINIMUM_ORDER = new BigDecimal("199.00");
    static final BigDecimal COUPON_RATE = new BigDecimal("0.20");
    static final BigDecimal COUPON_CAP = new BigDecimal("100.00");
    static final BigDecimal GST = new BigDecimal("0.05");

    public record Totals(BigDecimal subtotal, BigDecimal discount, BigDecimal tax, BigDecimal total) {
    }

    private Pricing() {
    }

    public static Totals price(List<Order.Line> lines, String coupon) {
        BigDecimal subtotal = BigDecimal.ZERO;
        for (Order.Line l : lines) {
            subtotal = subtotal.add(l.unitPrice().multiply(BigDecimal.valueOf(l.quantity())));
        }
        subtotal = money(subtotal);
        if (subtotal.compareTo(MINIMUM_ORDER) < 0) {
            throw new IllegalArgumentException("minimum order is Rs " + MINIMUM_ORDER);
        }
        BigDecimal discount = BigDecimal.ZERO;
        if ("FIRST20".equals(coupon)) {
            discount = money(subtotal.multiply(COUPON_RATE)).min(COUPON_CAP);
        }
        BigDecimal tax = money(subtotal.subtract(discount).multiply(GST));
        return new Totals(subtotal, money(discount), tax, money(subtotal.subtract(discount).add(tax)));
    }

    static BigDecimal money(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_EVEN);
    }
}
