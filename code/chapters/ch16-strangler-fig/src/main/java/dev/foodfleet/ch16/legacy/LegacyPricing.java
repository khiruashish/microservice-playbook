package dev.foodfleet.ch16.legacy;

import java.time.LocalDateTime;

/**
 * Pricing as it lives in the FoodFleet monolith: rupees as {@code double}, times as IST strings,
 * tiers as one-letter codes, results in an array. It has been "right" for six years, and its exact
 * behaviour (including its rounding) is now what customers expect. Do not tidy it: migrate it.
 */
public final class LegacyPricing {

    private LegacyPricing() {
    }

    /**
     * @param itemRupees item prices in rupees
     * @param distanceKm restaurant to customer
     * @param tierCode   "G" gold, "S" silver, null for none
     * @param orderTime  IST, "dd-MM-yyyy HH:mm"
     * @return {items, deliveryFee, surcharge, total} in rupees
     */
    public static double[] quote(double[] itemRupees, double distanceKm, String tierCode, String orderTime) {
        double items = 0;
        for (double price : itemRupees) {
            items += price;
        }
        double fee = distanceKm <= 3 ? 25 : 25 + Math.ceil(distanceKm - 3) * 8;
        if ("G".equals(tierCode)) {
            fee = 0;                                    // gold members: free delivery
        } else if ("S".equals(tierCode) && items >= 499) {
            fee = 0;                                    // silver: free above ₹499
        }
        int hour = LocalDateTime.parse(orderTime, LegacyFormats.LEGACY_TIME).getHour();
        double surcharge = 0;
        if (hour >= 22 || hour < 2) {
            surcharge = Math.round((items + fee) * 0.15 * 100) / 100.0;   // late-night 15%
        }
        double total = Math.round((items + fee + surcharge) * 100) / 100.0;
        return new double[] {items, fee, surcharge, total};
    }
}
