package dev.foodfleet.ch16.pricing;

/** The seam: the monolith's checkout depends on this, not on either implementation. */
public interface PricingService {

    PriceQuote quote(PriceRequest request);
}
