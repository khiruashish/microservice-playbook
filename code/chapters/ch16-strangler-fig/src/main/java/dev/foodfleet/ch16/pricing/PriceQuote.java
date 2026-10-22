package dev.foodfleet.ch16.pricing;

/** Everything in paise. */
public record PriceQuote(long itemsPaise, long deliveryFeePaise, long surchargePaise, long totalPaise) {
}
