package dev.foodfleet.ch21.paging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class CursorTest {

    @Test
    void roundTripsIncludingMicroseconds() {
        Cursor cursor = new Cursor(Instant.parse("2026-10-03T08:30:00.123456Z"), "o-4");

        assertThat(Cursor.decode(cursor.encode())).isEqualTo(cursor);
    }

    @Test
    void isOpaqueAndUrlSafe() {
        String token = new Cursor(Instant.parse("2026-10-03T08:30:00Z"), "o-4").encode();

        assertThat(token.matches("[A-Za-z0-9_-]+")).isTrue();
    }

    @Test
    void rejectsAnythingItDidNotIssue() {
        for (String bad : new String[] {"", "abc", "djF8bm90LWEtZGF0ZXxvLTQ", "djJ8MjAyNi0xMC0wM1QwODozMDowMFp8by00"}) {
            assertThatThrownBy(() -> Cursor.decode(bad)).isInstanceOf(Cursor.InvalidCursor.class);
        }
    }
}
