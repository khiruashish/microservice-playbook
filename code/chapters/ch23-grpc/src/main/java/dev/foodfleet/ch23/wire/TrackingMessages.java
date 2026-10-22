package dev.foodfleet.ch23.wire;

import java.time.Instant;
import java.util.Locale;

/**
 * The tracking.proto messages encoded two ways: protobuf binary (field numbers from the .proto) and
 * the canonical proto3 JSON mapping (camelCase names, enum names, RFC 3339 timestamps, int64 as
 * strings) that a REST/JSON version of the same API would send.
 */
public final class TrackingMessages {

    public enum Stage { STAGE_UNSPECIFIED, PLACED, PREPARING, PICKED_UP, DELIVERED, CANCELLED }

    public record GeoPoint(double latitude, double longitude) {
    }

    public record Rider(String riderId, String name, GeoPoint location) {
    }

    public record OrderStatus(String orderId, Stage stage, int etaMinutes, Rider rider, Instant updatedAt,
                              String restaurantName) {
    }

    private TrackingMessages() {
    }

    public static ProtoWriter point(GeoPoint p) {
        return new ProtoWriter().doubleField(1, p.latitude()).doubleField(2, p.longitude());
    }

    static ProtoWriter timestamp(Instant t) {
        return new ProtoWriter().varint(1, t.getEpochSecond()).varint(2, t.getNano());
    }

    public static ProtoWriter status(OrderStatus s) {
        ProtoWriter rider = s.rider() == null ? null : new ProtoWriter()
                .string(1, s.rider().riderId()).string(2, s.rider().name()).message(3, point(s.rider().location()));
        return new ProtoWriter()
                .string(1, s.orderId())
                .varint(2, s.stage().ordinal())
                .varint(3, s.etaMinutes())
                .message(4, rider)
                .message(5, s.updatedAt() == null ? null : timestamp(s.updatedAt()))
                .string(6, s.restaurantName());
    }

    /** TrackingEvent with the rider_location member of the oneof set. */
    public static byte[] riderMoved(String orderId, GeoPoint p, Instant at) {
        return new ProtoWriter().string(1, orderId).message(3, point(p)).message(4, timestamp(at)).toByteArray();
    }

    public static String json(OrderStatus s) {
        StringBuilder b = new StringBuilder("{");
        b.append("\"orderId\":\"").append(s.orderId()).append('"');
        b.append(",\"stage\":\"").append(s.stage()).append('"');
        b.append(",\"etaMinutes\":").append(s.etaMinutes());
        if (s.rider() != null) {
            b.append(",\"rider\":{\"riderId\":\"").append(s.rider().riderId()).append("\",\"name\":\"")
                    .append(s.rider().name()).append("\",\"location\":").append(json(s.rider().location())).append('}');
        }
        b.append(",\"updatedAt\":\"").append(s.updatedAt()).append('"');
        b.append(",\"restaurantName\":\"").append(s.restaurantName()).append('"');
        return b.append('}').toString();
    }

    public static String json(GeoPoint p) {
        return String.format(Locale.ROOT, "{\"latitude\":%s,\"longitude\":%s}", p.latitude(), p.longitude());
    }

    public static String riderMovedJson(String orderId, GeoPoint p, Instant at) {
        return "{\"orderId\":\"" + orderId + "\",\"riderLocation\":" + json(p) + ",\"at\":\"" + at + "\"}";
    }
}
