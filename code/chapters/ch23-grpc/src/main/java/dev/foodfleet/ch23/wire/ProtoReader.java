package dev.foodfleet.ch23.wire;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the wire format field by field. The important behaviour for API evolution: a field number
 * the reader doesn't know is skipped using its wire type, so an old client can read a message from
 * a newer server. That is what makes adding fields a compatible change.
 */
public final class ProtoReader {

    public record Field(int number, int wireType, long varint, byte[] bytes) {
        public String string() {
            return new String(bytes, StandardCharsets.UTF_8);
        }

        public double asDouble() {
            return Double.longBitsToDouble(varint);
        }
    }

    private final byte[] data;
    private int pos;

    public ProtoReader(byte[] data) {
        this.data = data;
    }

    public List<Field> fields() {
        List<Field> fields = new ArrayList<>();
        while (pos < data.length) {
            long tag = readVarint();
            int number = (int) (tag >>> 3);
            int wireType = (int) (tag & 7);
            switch (wireType) {
                case ProtoWriter.VARINT -> fields.add(new Field(number, wireType, readVarint(), null));
                case ProtoWriter.FIXED64 -> {
                    long bits = 0;
                    for (int i = 0; i < 8; i++) {
                        bits |= (long) (data[pos++] & 0xff) << (8 * i);
                    }
                    fields.add(new Field(number, wireType, bits, null));
                }
                case ProtoWriter.LENGTH_DELIMITED -> {
                    int length = (int) readVarint();
                    byte[] value = new byte[length];
                    System.arraycopy(data, pos, value, 0, length);
                    pos += length;
                    fields.add(new Field(number, wireType, 0, value));
                }
                case 5 -> {                                   // fixed32: skip 4 bytes
                    pos += 4;
                    fields.add(new Field(number, wireType, 0, null));
                }
                default -> throw new IllegalArgumentException("unsupported wire type " + wireType);
            }
        }
        return fields;
    }

    private long readVarint() {
        long result = 0;
        for (int shift = 0; shift < 64; shift += 7) {
            byte b = data[pos++];
            result |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return result;
            }
        }
        throw new IllegalArgumentException("malformed varint");
    }
}
