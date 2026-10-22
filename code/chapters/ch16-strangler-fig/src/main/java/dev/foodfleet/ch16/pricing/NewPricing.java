package dev.foodfleet.ch16.pricing;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Set;

/**
 * pricing-service, the new implementation, in the three releases the team shadowed before taking any
 * traffic. Each bug is one a real rewrite makes when it reimplements rules from reading old code.
 * <ul>
 *   <li>V1: late-night hours in UTC instead of IST; forgot gold members' free delivery;
 *       surcharge rounded down</li>
 *   <li>V2: time zone and gold fixed; surcharge still rounded down</li>
 *   <li>V3: surcharge rounded half-up, as the team believed the monolith did</li>
 * </ul>
 * Zones are a new concept: pricing-service refuses zones its data doesn't know.
 */
public final class NewPricing implements PricingService {

    public enum Release { V1, V2, V3 }

    public static final class UnknownZone extends RuntimeException {
        public UnknownZone(String zone) {
            super("no pricing data for zone " + zone, null, false, false);
        }
    }

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final Release release;
    private final Set<String> knownZones;

    public NewPricing(Release release, Set<String> knownZones) {
        this.release = release;
        this.knownZones = Set.copyOf(knownZones);
    }

    @Override
    public PriceQuote quote(PriceRequest request) {
        if (!knownZones.contains(request.zone())) {
            throw new UnknownZone(request.zone());
        }
        long items = request.itemsTotalPaise();
        long fee = deliveryFee(request, items);
        long surcharge = lateNight(request) ? surcharge(items + fee) : 0;
        return new PriceQuote(items, fee, surcharge, items + fee + surcharge);
    }

    private long deliveryFee(PriceRequest request, long items) {
        long extraKm = Math.max(0, (request.distanceMeters() - 3_000 + 999) / 1_000);   // ceil
        long fee = 2_500 + extraKm * 800;
        boolean gold = request.tier() == PriceRequest.Tier.GOLD;
        if (gold && release != Release.V1) {                     // V1 bug: forgot gold
            return 0;
        }
        if (request.tier() == PriceRequest.Tier.SILVER && items >= 49_900) {
            return 0;
        }
        return fee;
    }

    private boolean lateNight(PriceRequest request) {
        ZoneId zone = release == Release.V1 ? ZoneOffset.UTC : IST;   // V1 bug: UTC
        int hour = request.orderedAt().atZone(zone).getHour();
        return hour >= 22 || hour < 2;
    }

    private long surcharge(long base) {
        return release == Release.V3
                ? Math.floorDiv(base * 15 + 50, 100)                  // half-up
                : base * 15 / 100;                                    // V1, V2 bug: rounds down
    }
}
