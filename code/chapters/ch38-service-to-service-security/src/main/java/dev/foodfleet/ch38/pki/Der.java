package dev.foodfleet.ch38.pki;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * The few DER encodings an X.509 certificate needs (ITU-T X.690). Only used by {@link DevCa}.
 */
final class Der {

    private Der() {
    }

    static byte[] tlv(int tag, byte[] value) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(tag);
        int n = value.length;
        if (n < 0x80) {
            out.write(n);
        } else {
            byte[] len = BigInteger.valueOf(n).toByteArray();
            int skip = len[0] == 0 ? 1 : 0;
            out.write(0x80 | (len.length - skip));
            out.write(len, skip, len.length - skip);
        }
        out.writeBytes(value);
        return out.toByteArray();
    }

    static byte[] sequence(byte[]... parts) {
        return tlv(0x30, concat(parts));
    }

    static byte[] set(byte[]... parts) {
        return tlv(0x31, concat(parts));
    }

    /** Context-specific, constructed, explicit tag [n]. */
    static byte[] explicit(int n, byte[] inner) {
        return tlv(0xA0 | n, inner);
    }

    static byte[] integer(BigInteger value) {
        return tlv(0x02, value.toByteArray());
    }

    static byte[] bool(boolean value) {
        return tlv(0x01, new byte[] {(byte) (value ? 0xFF : 0x00)});
    }

    static byte[] octetString(byte[] value) {
        return tlv(0x04, value);
    }

    static byte[] bitString(byte[] value, int unusedBits) {
        byte[] v = new byte[value.length + 1];
        v[0] = (byte) unusedBits;
        System.arraycopy(value, 0, v, 1, value.length);
        return tlv(0x03, v);
    }

    static byte[] utf8(String s) {
        return tlv(0x0C, s.getBytes(StandardCharsets.UTF_8));
    }

    static byte[] nul() {
        return new byte[] {0x05, 0x00};
    }

    /** UTCTime for 1950–2049, GeneralizedTime otherwise (RFC 5280 §4.1.2.5). */
    static byte[] time(Instant t) {
        var utc = t.atZone(ZoneOffset.UTC);
        if (utc.getYear() >= 1950 && utc.getYear() < 2050) {
            return tlv(0x17, DateTimeFormatter.ofPattern("yyMMddHHmmss'Z'").format(utc)
                    .getBytes(StandardCharsets.US_ASCII));
        }
        return tlv(0x18, DateTimeFormatter.ofPattern("yyyyMMddHHmmss'Z'").format(utc)
                .getBytes(StandardCharsets.US_ASCII));
    }

    static byte[] oid(String dotted) {
        String[] p = dotted.split("\\.");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(Integer.parseInt(p[0]) * 40 + Integer.parseInt(p[1]));
        for (int i = 2; i < p.length; i++) {
            long v = Long.parseLong(p[i]);
            byte[] tmp = new byte[10];
            int k = tmp.length;
            tmp[--k] = (byte) (v & 0x7F);
            v >>>= 7;
            while (v > 0) {
                tmp[--k] = (byte) (0x80 | (v & 0x7F));
                v >>>= 7;
            }
            out.write(tmp, k, tmp.length - k);
        }
        return tlv(0x06, out.toByteArray());
    }

    static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] p : parts) {
            out.writeBytes(p);
        }
        return out.toByteArray();
    }
}
