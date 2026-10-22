package dev.foodfleet.ch23.wire;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.zip.GZIPOutputStream;

/**
 * Bytes on the wire for the same data as protobuf and as JSON, raw and gzipped: one order status,
 * and a 30-minute delivery's rider positions (one every 5 seconds).
 */
public final class PayloadComparison {

    static final TrackingMessages.OrderStatus SAMPLE = new TrackingMessages.OrderStatus("o-20261006-000042",
            TrackingMessages.Stage.PICKED_UP, 17,
            new TrackingMessages.Rider("r-7731", "Suresh K", new TrackingMessages.GeoPoint(12.935192, 77.624481)),
            Instant.parse("2026-10-06T13:12:45.120Z"), "Meghana Foods, Koramangala");

    static int gzipped(byte[] data) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            gzip.write(data);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return bytes.size();
    }

    /** Raw sizes; gzip of everything at once; and gzip applied to each message on its own. */
    public record Sizes(int protobuf, int json, int protobufGzip, int jsonGzip, int protobufGzipEach, int jsonGzipEach) {
    }

    public static Sizes single() {
        byte[] proto = TrackingMessages.status(SAMPLE).toByteArray();
        byte[] json = TrackingMessages.json(SAMPLE).getBytes(StandardCharsets.UTF_8);
        return new Sizes(proto.length, json.length, gzipped(proto), gzipped(json), gzipped(proto), gzipped(json));
    }

    /** 360 position updates as separate messages (as a stream sends them), sizes summed. */
    public static Sizes ride() {
        int proto = 0;
        int json = 0;
        int protoEach = 0;
        int jsonEach = 0;
        ByteArrayOutputStream allProto = new ByteArrayOutputStream();
        ByteArrayOutputStream allJson = new ByteArrayOutputStream();
        Instant t = Instant.parse("2026-10-06T13:00:00Z");
        double lat = 12.935192;
        double lng = 77.624481;
        for (int i = 0; i < 360; i++) {
            lat += 0.00011 * Math.sin(i / 17.0);
            lng += 0.00013 * Math.cos(i / 23.0);
            TrackingMessages.GeoPoint p = new TrackingMessages.GeoPoint(Math.round(lat * 1e6) / 1e6, Math.round(lng * 1e6) / 1e6);
            byte[] pb = TrackingMessages.riderMoved("o-20261006-000042", p, t.plusSeconds(5L * i));
            byte[] js = TrackingMessages.riderMovedJson("o-20261006-000042", p, t.plusSeconds(5L * i))
                    .getBytes(StandardCharsets.UTF_8);
            proto += pb.length;
            json += js.length;
            protoEach += gzipped(pb);
            jsonEach += gzipped(js);
            allProto.writeBytes(pb);
            allJson.writeBytes(js);
        }
        return new Sizes(proto, json, gzipped(allProto.toByteArray()), gzipped(allJson.toByteArray()), protoEach, jsonEach);
    }

    public static void main(String[] args) {
        System.out.println("OrderStatus as protobuf (hex): " + HexFormat.of().formatHex(TrackingMessages.status(SAMPLE).toByteArray()));
        System.out.println("OrderStatus as JSON: " + TrackingMessages.json(SAMPLE) + "\n");
        Sizes one = single();
        Sizes ride = ride();
        System.out.printf("%-48s %9s %9s %7s%n", "bytes on the wire", "protobuf", "JSON", "ratio");
        row("one OrderStatus", one.protobuf(), one.json());
        row("one OrderStatus, gzip", one.protobufGzip(), one.jsonGzip());
        row("30-min ride: 360 positions, raw", ride.protobuf(), ride.json());
        row("30-min ride, each message gzipped (gRPC-style)", ride.protobufGzipEach(), ride.jsonGzipEach());
        row("30-min ride, whole stream gzipped at once", ride.protobufGzip(), ride.jsonGzip());
    }

    private static void row(String label, int protobuf, int json) {
        System.out.printf("%-48s %9d %9d %6.2fx%n", label, protobuf, json, (double) json / protobuf);
    }
}
