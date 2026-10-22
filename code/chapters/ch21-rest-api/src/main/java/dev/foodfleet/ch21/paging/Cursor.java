package dev.foodfleet.ch21.paging;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;

/**
 * A keyset pagination cursor: the sort key of the last item on the page (placed_at, then id as the
 * tie-breaker). Clients get it as an opaque token and send it back; they must not build or parse
 * it, so the server can change its contents. The "v1" prefix lets a later format coexist.
 */
public record Cursor(Instant placedAt, String orderId) {

    public static final class InvalidCursor extends RuntimeException {
        public InvalidCursor(String token) {
            super("not a valid page cursor: " + token);
        }
    }

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    public String encode() {
        return ENCODER.encodeToString(("v1|" + placedAt + "|" + orderId).getBytes(StandardCharsets.UTF_8));
    }

    public static Cursor decode(String token) {
        try {
            String[] parts = new String(DECODER.decode(token), StandardCharsets.UTF_8).split("\\|", 3);
            if (parts.length != 3 || !parts[0].equals("v1") || parts[2].isBlank()) {
                throw new InvalidCursor(token);
            }
            return new Cursor(Instant.parse(parts[1]), parts[2]);
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw new InvalidCursor(token);
        }
    }
}
