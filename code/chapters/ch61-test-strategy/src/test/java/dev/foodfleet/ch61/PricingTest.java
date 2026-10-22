package dev.foodfleet.ch61;

import dev.foodfleet.ch61.order.Order;
import dev.foodfleet.ch61.order.Pricing;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Unit tests: the money rules, no I/O. */
@Tag("unit")
class PricingTest {

    static Order.Line line(String price, int qty) {
        return new Order.Line("i", qty, new BigDecimal(price));
    }

    @Test
    void taxIsChargedOnTheDiscountedAmount() {
        Pricing.Totals t = Pricing.price(List.of(line("150.00", 2)), "FIRST20");
        assertEquals(new BigDecimal("300.00"), t.subtotal());
        assertEquals(new BigDecimal("60.00"), t.discount());
        assertEquals(new BigDecimal("12.00"), t.tax());
        assertEquals(new BigDecimal("252.00"), t.total());
    }

    @Test
    void theCouponIsCappedAtOneHundred() {
        Pricing.Totals t = Pricing.price(List.of(line("400.00", 2)), "FIRST20");
        assertEquals(new BigDecimal("100.00"), t.discount());
        assertEquals(new BigDecimal("735.00"), t.total());
    }

    @Test
    void amountsAreRoundedHalfEven() {
        // 5% of 210.50 is 10.525: half-even rounds to 10.52 (half-up would give 10.53)
        assertEquals(new BigDecimal("10.52"), Pricing.price(List.of(line("210.50", 1)), null).tax());
    }

    @Test
    void theMinimumOrderIsInclusive() {
        assertEquals(new BigDecimal("199.00"), Pricing.price(List.of(line("199.00", 1)), null).subtotal());
        assertThrows(IllegalArgumentException.class, () -> Pricing.price(List.of(line("198.99", 1)), null));
    }
}
