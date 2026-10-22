package dev.foodfleet.ch16.rollout;

import dev.foodfleet.ch16.pricing.PriceRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Seeded, FoodFleet-shaped pricing traffic: 20,000 customers (10% gold, 30% silver), 1–4 items at
 * menu prices (whole and half rupees), 0.5–9 km, any time of a week in October 2026, twelve zones.
 */
public final class Traffic {

    public static final List<String> ZONES = List.of("KORAMANGALA", "INDIRANAGAR", "HSR", "JAYANAGAR",
            "BTM", "MARATHAHALLI", "BELLANDUR", "HEBBAL", "YELAHANKA", "JP-NAGAR", "ELECTRONIC-CITY", "WHITEFIELD");

    private static final long[] MENU_PAISE = {9_900, 12_950, 14_900, 17_950, 19_900, 22_450, 24_900, 29_950,
            34_900, 39_950, 44_900, 54_950};
    private static final Instant WEEK_START = Instant.parse("2026-10-04T18:30:00Z");   // Mon 00:00 IST

    private final Random random;

    public Traffic(long seed) {
        this.random = new Random(seed);
    }

    public static Set<String> allZones() {
        return Set.copyOf(ZONES);
    }

    public PriceRequest next() {
        int customer = random.nextInt(20_000);
        double t = random.nextDouble();
        PriceRequest.Tier tier = customer % 10 == 0 ? PriceRequest.Tier.GOLD
                : customer % 10 < 4 ? PriceRequest.Tier.SILVER : PriceRequest.Tier.NONE;
        List<Long> items = new ArrayList<>();
        for (int i = 0, n = 1 + random.nextInt(4); i < n; i++) {
            items.add(MENU_PAISE[random.nextInt(MENU_PAISE.length)]);
        }
        String zone = t < 0.03 ? "WHITEFIELD" : ZONES.get(random.nextInt(ZONES.size() - 1));
        return new PriceRequest("c-" + customer, tier, items, 500 + random.nextInt(8_500),
                WEEK_START.plusSeconds(random.nextInt(7 * 24 * 3600)), zone);
    }
}
