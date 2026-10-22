package dev.foodfleet.ch05.ordering.domain;

import java.util.Collection;

/**
 * Domain service: logic that belongs to the domain but not naturally to one entity. Pricing needs
 * the order's lines, the restaurant's terms and the delivery zone's fee, so it lives here rather
 * than in Order or in an application service.
 *
 * <p>Tax treatment is simplified for teaching (5% GST on food and packaging, delivery fee shown
 * separately). It's not tax advice; real GST rules depend on the supplier and service type.
 */
public final class PricingPolicy {

    public static final int GST_BASIS_POINTS = 500; // 5%

    public PriceBreakdown price(Collection<OrderLine> lines, RestaurantTerms terms, Money deliveryFee) {
        Money itemTotal = lines.stream().map(OrderLine::lineTotal).reduce(Money.ZERO, Money::plus);
        int itemCount = lines.stream().mapToInt(line -> line.quantity().value()).sum();
        Money packaging = terms.packagingChargePerItem().times(itemCount);
        Money gst = itemTotal.plus(packaging).percentage(GST_BASIS_POINTS);
        Money total = itemTotal.plus(packaging).plus(gst).plus(deliveryFee);
        return new PriceBreakdown(itemTotal, packaging, gst, deliveryFee, total);
    }
}
