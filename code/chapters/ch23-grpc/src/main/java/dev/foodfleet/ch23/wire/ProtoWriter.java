package dev.foodfleet.ch23.wire;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * The Protocol Buffers wire format, the parts this chapter's messages use, written out by hand to
 * show what protoc-generated code does: every field is (field number << 3 | wire type) followed by
 * the value. Varints for integers and enums, 8 bytes for doubles, length-prefixed bytes for strings
 * and nested messages. Default values (0, "", empty) are not written at all in proto3.
 */
public final class ProtoWriter {

    static final int VARINT = 0;
    static final int FIXED64 = 1;
    static final int LENGTH_DELIMITED = 2;

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    public ProtoWriter varint(int field, long value) {
        if (value != 0) {
            tag(field, VARINT);
            rawVarint(value);
        }
        return this;
    }

    public ProtoWriter doubleField(int field, double value) {
        if (Double.doubleToRawLongBits(value) != 0) {
            tag(field, FIXED64);
            long bits = Double.doubleToRawLongBits(value);
            for (int i = 0; i < 8; i++) {
                out.write((int) (bits >>> (8 * i)) & 0xff);          // little-endian
            }
        }
        return this;
    }

    public ProtoWriter string(int field, String value) {
        if (value != null && !value.isEmpty()) {
            bytes(field, value.getBytes(StandardCharsets.UTF_8));
        }
        return this;
    }

    public ProtoWriter message(int field, ProtoWriter nested) {
        if (nested != null) {
            bytes(field, nested.toByteArray());                 // present, even if empty
        }
        return this;
    }

    private void bytes(int field, byte[] value) {
        tag(field, LENGTH_DELIMITED);
        rawVarint(value.length);
        out.writeBytes(value);
    }

    private void tag(int field, int wireType) {
        rawVarint(((long) field << 3) | wireType);
    }

    /** 7 bits per byte, low bits first, high bit set on every byte but the last. */
    private void rawVarint(long value) {
        while ((value & ~0x7FL) != 0) {
            out.write((int) ((value & 0x7F) | 0x80));
            value >>>= 7;
        }
        out.write((int) value);
    }

    public byte[] toByteArray() {
        return out.toByteArray();
    }
}
