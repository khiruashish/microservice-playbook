package dev.foodfleet.ch23.wire;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The hand-written encoder against bytes produced by Google's protobuf runtime for the same message
 * (verify/verify_wire.py), and the reader's handling of fields it doesn't know.
 */
class ProtoWireTest {

    /** OrderStatus SAMPLE encoded by the official protobuf runtime (python protobuf 7.34.1). */
    static final String OFFICIAL_HEX = "0a116f2d32303236313030362d3030303034321003181122260a06722d373733311208537572657368204b"
            + "1a120930f65e7cd1de2940114cfe277ff76753402a0b08cde793d60610809c9c39321a4d656768616e6120466f6f64732c204b"
            + "6f72616d616e67616c61";

    @Test
    void encodesExactlyLikeTheOfficialRuntime() {
        byte[] bytes = TrackingMessages.status(PayloadComparison.SAMPLE).toByteArray();

        assertThat(HexFormat.of().formatHex(bytes)).isEqualTo(OFFICIAL_HEX);
        assertThat(bytes.length).isEqualTo(104);
    }

    @Test
    void fieldNumbersNotNamesAreOnTheWire() {
        List<ProtoReader.Field> fields = new ProtoReader(TrackingMessages.status(PayloadComparison.SAMPLE).toByteArray()).fields();

        assertThat(fields.stream().map(ProtoReader.Field::number).toList()).isEqualTo(List.of(1, 2, 3, 4, 5, 6));
        assertThat(fields.get(0).string()).isEqualTo("o-20261006-000042");
        assertThat(fields.get(1).varint()).isEqualTo(3L);                       // PICKED_UP
        assertThat(fields.get(2).varint()).isEqualTo(17L);
    }

    @Test
    void defaultValuesAreNotWrittenInProto3() {
        var placed = new TrackingMessages.OrderStatus("o-1", TrackingMessages.Stage.STAGE_UNSPECIFIED, 0, null,
                null, "");

        assertThat(TrackingMessages.status(placed).toByteArray().length).isEqualTo(5);   // only order_id
    }

    @Test
    void anOldReaderSkipsAFieldAddedLater() {
        // v2 of the schema adds `Timestamp promised_by = 8;` — a v1 reader has never heard of it
        byte[] v1 = TrackingMessages.status(PayloadComparison.SAMPLE).toByteArray();
        byte[] v2 = concat(v1, new ProtoWriter().message(8, new ProtoWriter().varint(1,
                Instant.parse("2026-10-06T13:30:00Z").getEpochSecond())).toByteArray());

        List<ProtoReader.Field> fields = new ProtoReader(v2).fields();

        assertThat(fields.size()).isEqualTo(7);
        assertThat(fields.get(0).string()).isEqualTo("o-20261006-000042");     // known fields intact
        assertThat(fields.get(6).number()).isEqualTo(8);                        // unknown one carried, not fatal
    }

    @Test
    void protobufIsSmallerPerMessageButGzipOfAWholeJsonStreamWins() {
        PayloadComparison.Sizes one = PayloadComparison.single();
        PayloadComparison.Sizes ride = PayloadComparison.ride();

        assertThat(one.protobuf()).isEqualTo(104);
        assertThat(one.json()).isEqualTo(255);
        assertThat(ride.protobuf()).isLessThan(ride.json() / 2);
        assertThat(ride.protobufGzipEach()).isGreaterThan(ride.protobuf());      // gzip per small message hurts
        assertThat(ride.jsonGzip()).isLessThan(ride.protobufGzip());             // repetitive text compresses well
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}
