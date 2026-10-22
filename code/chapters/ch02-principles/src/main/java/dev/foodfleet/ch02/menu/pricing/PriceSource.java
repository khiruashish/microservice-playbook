package dev.foodfleet.ch02.menu.pricing;

/** Where the price multiplier came from. Exposed in API v2 so clients and support can see degradation. */
public enum PriceSource {
    /** Fresh answer from pricing-service. */
    LIVE,
    /** pricing-service unavailable; last good value, younger than the staleness limit. */
    LAST_KNOWN,
    /** pricing-service unavailable and nothing recent is known; base price (1.0×). */
    BASE
}
