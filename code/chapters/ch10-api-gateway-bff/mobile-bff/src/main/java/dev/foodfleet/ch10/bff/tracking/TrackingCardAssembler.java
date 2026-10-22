package dev.foodfleet.ch10.bff.tracking;

import dev.foodfleet.ch10.bff.client.Downstream.Eta;
import dev.foodfleet.ch10.bff.client.Downstream.OrderSummary;
import dev.foodfleet.ch10.bff.client.Downstream.Restaurant;
import dev.foodfleet.ch10.bff.client.Downstream.Rider;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Pure presentation logic for one screen: no I/O, so every rule is a fast unit test. This is the
 * code a BFF exists for; it would otherwise be duplicated in the Android, iOS and web apps.
 */
public final class TrackingCardAssembler {

    static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("h:mm", Locale.ENGLISH);

    public TrackingCard assemble(OrderSummary order, Optional<Eta> eta, Optional<Restaurant> restaurant,
                                 Optional<Rider> rider, List<String> failed, Instant now) {
        boolean finished = order.status().equals("DELIVERED") || order.status().equals("CANCELLED");

        String etaText = null;
        Boolean approximate = null;
        if (!finished && eta.isPresent()) {
            etaText = "Arriving " + window(now, eta.get().window().from(), eta.get().window().to());
            approximate = eta.get().degraded();
        }

        TrackingCard.RiderLine riderLine = rider.map(r -> new TrackingCard.RiderLine(r.name(), r.vehicle())).orElse(null);

        List<String> actions = new ArrayList<>();
        if (riderLine != null && order.status().equals("PICKED_UP")) {
            actions.add("CALL_RIDER");
        }
        if (order.status().equals("PLACED")) {
            actions.add("CANCEL");
        }
        actions.add("HELP");

        return new TrackingCard(
                order.orderId(),
                title(order.status()),
                restaurant.map(r -> "from " + r.name() + ", " + r.area()).orElse(null),
                etaText,
                approximate,
                finished ? null : riderLine,
                rupees(order.totalPaise()),
                List.copyOf(actions),
                List.copyOf(failed));
    }

    static String title(String status) {
        return switch (status) {
            case "PLACED" -> "Waiting for the restaurant to accept";
            case "ACCEPTED", "PREPARING" -> "Your food is being prepared";
            case "PICKED_UP" -> "On the way";
            case "DELIVERED" -> "Delivered";
            case "CANCELLED" -> "Order cancelled";
            default -> "Order update";   // tolerant: a new status must not crash old screens
        };
    }

    /** "8:24–8:30 pm", or "11:55 am–12:05 pm" when the window crosses noon or midnight. */
    static String window(Instant now, int fromMinutes, int toMinutes) {
        LocalTime from = now.plusSeconds(fromMinutes * 60L).atZone(IST).toLocalTime();
        LocalTime to = now.plusSeconds(toMinutes * 60L).atZone(IST).toLocalTime();
        String fromSuffix = suffix(from);
        String toSuffix = suffix(to);
        return fromSuffix.equals(toSuffix)
                ? CLOCK.format(from) + "–" + CLOCK.format(to) + " " + toSuffix
                : CLOCK.format(from) + " " + fromSuffix + "–" + CLOCK.format(to) + " " + toSuffix;
    }

    private static String suffix(LocalTime time) {
        return time.getHour() < 12 ? "am" : "pm";
    }

    /** Indian digit grouping: ₹1,23,456.50 */
    static String rupees(long paise) {
        long rupees = paise / 100;
        String digits = Long.toString(rupees);
        StringBuilder grouped = new StringBuilder();
        if (digits.length() <= 3) {
            grouped.append(digits);
        } else {
            String head = digits.substring(0, digits.length() - 3);
            String tail = digits.substring(digits.length() - 3);
            for (int i = 0; i < head.length(); i++) {
                if (i > 0 && (head.length() - i) % 2 == 0) {
                    grouped.append(',');
                }
                grouped.append(head.charAt(i));
            }
            grouped.append(',').append(tail);
        }
        return String.format(Locale.ROOT, "₹%s.%02d", grouped, paise % 100);
    }
}
