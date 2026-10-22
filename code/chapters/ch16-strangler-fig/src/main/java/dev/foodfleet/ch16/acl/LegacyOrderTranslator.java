package dev.foodfleet.ch16.acl;

import dev.foodfleet.ch16.legacy.LegacyFormats;
import dev.foodfleet.ch16.legacy.LegacyOrderRow;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Optional;

/**
 * The anti-corruption layer between the monolith's ORDERS table and the new order-history service.
 * Every legacy quirk is handled here, once, with a test; nothing legacy-shaped leaks past it.
 */
public final class LegacyOrderTranslator {

    static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private static final Map<String, Order.Status> STATUS = Map.of(
            "P", Order.Status.PLACED,
            "K", Order.Status.PREPARING,
            "D", Order.Status.DELIVERED,
            "X", Order.Status.CANCELLED,
            "R", Order.Status.REFUNDED);

    public Translation translate(LegacyOrderRow row) {
        Order.Status status = STATUS.get(row.ordSts() == null ? "" : row.ordSts().trim().toUpperCase());
        if (status == null) {
            return new Translation.Quarantined(row.ordId(), "unknown status '" + row.ordSts() + "'");
        }
        if (row.amt() <= 0 || Double.isNaN(row.amt())) {
            return new Translation.Quarantined(row.ordId(), "non-positive amount " + row.amt());
        }
        Optional<Instant> deliveredAt;
        try {
            deliveredAt = Optional.ofNullable(row.dlvTs()).filter(s -> !s.isBlank())
                    .map(s -> LocalDateTime.parse(s.trim(), LegacyFormats.LEGACY_TIME).atZone(IST).toInstant());
        } catch (DateTimeParseException e) {
            return new Translation.Quarantined(row.ordId(), "unreadable delivery time '" + row.dlvTs() + "'");
        }
        if (status == Order.Status.DELIVERED && deliveredAt.isEmpty()) {
            return new Translation.Quarantined(row.ordId(), "delivered without a delivery time");
        }
        return new Translation.Translated(new Order(
                row.ordId().trim(), status, paise(row.amt()), tier(row.custTier()), address(row.addrLine()),
                deliveredAt));
    }

    /** Rupees as double to exact paise: via the decimal string, never via binary arithmetic. */
    static long paise(double rupees) {
        return BigDecimal.valueOf(rupees).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    static Order.Tier tier(String code) {
        if (code == null) {
            return Order.Tier.NONE;
        }
        return switch (code.trim().toUpperCase()) {
            case "G" -> Order.Tier.GOLD;
            case "S" -> Order.Tier.SILVER;
            default -> Order.Tier.NONE;            // "", and the "N" some 2019 rows have
        };
    }

    static Order.Address address(String line) {
        String[] parts = (line == null ? "" : line).split("\\|", -1);
        String flat = part(parts, 0);
        String street = part(parts, 1);
        String area = part(parts, 2);
        Optional<String> pincode = Optional.of(part(parts, 3)).filter(p -> p.matches("\\d{6}"));
        return new Order.Address(flat, street, area, pincode);
    }

    private static String part(String[] parts, int i) {
        return i < parts.length ? parts[i].trim() : "";
    }
}
