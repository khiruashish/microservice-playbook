package dev.foodfleet.ch39.secret;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

/**
 * A secret value that does not leak through the usual accidents: {@code toString()}, string concatenation in log
 * statements, exception messages and debugger views print a mask; equality is constant-time; and the characters can
 * be wiped when the secret is replaced. Code that needs the value asks for it explicitly with {@link #reveal()}.
 * A wrapper cannot stop a heap dump or a library that copies the value into a String (JDBC drivers do): it stops the
 * leaks your own code would otherwise make.
 */
public final class Secret {

    private final char[] value;
    private volatile boolean destroyed;

    private Secret(char[] value) {
        this.value = value;
    }

    public static Secret of(char[] value) {
        return new Secret(value.clone());
    }

    public static Secret of(byte[] utf8) {
        CharBuffer chars = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(utf8));
        char[] copy = Arrays.copyOf(chars.array(), chars.limit());
        Arrays.fill(chars.array(), '\0');
        return new Secret(copy);
    }

    /** The value, as a copy the caller should wipe when done. */
    public char[] reveal() {
        if (destroyed) {
            throw new IllegalStateException("secret was destroyed (rotated or closed)");
        }
        return value.clone();
    }

    /** For APIs that only take a String (JDBC, HTTP headers): the copy cannot be wiped. */
    public String revealAsString() {
        return new String(reveal());
    }

    /** Overwrites the characters; later {@link #reveal()} calls fail. */
    public void destroy() {
        destroyed = true;
        Arrays.fill(value, '\0');
    }

    public boolean isDestroyed() {
        return destroyed;
    }

    /** Constant-time comparison: how long it takes doesn't reveal how many leading characters matched. */
    public boolean matches(char[] candidate) {
        byte[] a = utf8(value);
        byte[] b = utf8(candidate);
        try {
            return MessageDigest.isEqual(a, b);
        } finally {
            Arrays.fill(a, (byte) 0);
            Arrays.fill(b, (byte) 0);
        }
    }

    private static byte[] utf8(char[] chars) {
        ByteBuffer buffer = StandardCharsets.UTF_8.encode(CharBuffer.wrap(chars));
        byte[] bytes = Arrays.copyOf(buffer.array(), buffer.limit());
        Arrays.fill(buffer.array(), (byte) 0);
        return bytes;
    }

    @Override
    public String toString() {
        return "Secret[****]";
    }

    /** Secrets are compared with {@link #matches}, never with equals or as map keys. */
    @Override
    public boolean equals(Object other) {
        return this == other;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }
}
