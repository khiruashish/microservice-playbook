package dev.foodfleet.ch16.pricing;

import dev.foodfleet.ch16.legacy.LegacyFormats;
import dev.foodfleet.ch16.legacy.LegacyPricing;
import java.time.ZoneId;

/**
 * The legacy implementation behind the new interface. This adapter is an anti-corruption layer in
 * the other direction: new model in, legacy call, new model out.
 */
public final class LegacyPricingAdapter implements PricingService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Override
    public PriceQuote quote(PriceRequest request) {
        double[] items = request.itemsPaise().stream().mapToDouble(p -> p / 100.0).toArray();
        String tier = switch (request.tier()) {
            case GOLD -> "G";
            case SILVER -> "S";
            case NONE -> null;
        };
        String time = request.orderedAt().atZone(IST).format(LegacyFormats.LEGACY_TIME);
        double[] q = LegacyPricing.quote(items, request.distanceMeters() / 1000.0, tier, time);
        return new PriceQuote(Math.round(q[0] * 100), Math.round(q[1] * 100), Math.round(q[2] * 100),
                Math.round(q[3] * 100));
    }
}
