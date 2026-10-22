package dev.foodfleet.ch02.menu.pricing;

import java.util.Objects;

public record SurgeQuote(String zone, int multiplierBps, PriceSource source) {

    public static final int BASE_BPS = 10_000;

    public SurgeQuote {
        Objects.requireNonNull(zone, "zone");
        Objects.requireNonNull(source, "source");
    }

    /** Applies the multiplier with half-up rounding to the nearest paisa, in pure integer arithmetic. */
    public long apply(long basePricePaise) {
        return Math.addExact(Math.multiplyExact(basePricePaise, multiplierBps), 5_000) / 10_000;
    }
}
