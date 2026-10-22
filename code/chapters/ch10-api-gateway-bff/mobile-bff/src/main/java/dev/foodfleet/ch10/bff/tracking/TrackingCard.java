package dev.foodfleet.ch10.bff.tracking;

import java.util.List;

/**
 * Exactly what the app's order-tracking screen renders, already formatted: the app does no
 * joining, no date arithmetic and no currency formatting. Absent parts are {@code null} and left
 * out of the JSON; {@code degraded} names parts that failed to load, so the app can say so.
 */
public record TrackingCard(
        String orderId,
        String title,
        String subtitle,
        String etaText,
        Boolean etaApproximate,
        RiderLine rider,
        String total,
        List<String> actions,
        List<String> degraded) {

    public record RiderLine(String name, String vehicle) {
    }
}
