package dev.foodfleet.ch40.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class FreeTextTest {

    @Test
    void whatCustomersTypeIsAccepted() {
        for (String note : new String[] {
                "Please don't add onions; select the less spicy one",
                "Ring the bell twice -- the dog will bark",
                "Gate 3, 'B' block, 2nd floor (near the drop-off point)",
                "Birthday surprise <3 write 'Happy B'day Asha' on the box :)",
                "दाल मखनी extra butter 🧈 please!!",
                "ಬಿಸಿ ಬೇಳೆ ಬಾತ್, ಕಡಿಮೆ ಖಾರ",
                "👨‍👩‍👧 family pack"}) {
            assertThat(FreeText.clean("note", note, 500, true)).isEqualTo(note);
        }
    }

    @Test
    void normalisesSoTheSameTextIsStoredTheSameWay() {
        String decomposed = "Café Coffee Day";                 // e + combining acute
        String composed = "Café Coffee Day";
        assertThat(FreeText.clean("name", decomposed, 100, false)).isEqualTo(composed);
        assertThat(FreeText.clean("name", "  padded  ", 100, false)).isEqualTo("padded");
        assertThat(FreeText.clean("note", "a\r\nb", 100, true)).isEqualTo("a\nb");
    }

    @Test
    void countsCodePointsNotUtf16Units() {
        String emoji = "🍕".repeat(10);                                // 20 chars, 10 code points
        assertThat(FreeText.clean("note", emoji, 10, false)).isEqualTo(emoji);
        assertThatThrownBy(() -> FreeText.clean("note", emoji + "🍕", 10, false)).hasMessageContaining("too long");
    }

    @Test
    void rejectsWhatIsNotText() {
        assertThatThrownBy(() -> FreeText.clean("note", "abc\u0000def", 100, true)).hasMessageContaining("control character");
        assertThatThrownBy(() -> FreeText.clean("note", "line1\nline2", 100, false)).hasMessageContaining("single line");
        assertThatThrownBy(() -> FreeText.clean("note", "refund ‮regdab‬ ok", 100, true))
                .hasMessageContaining("bidirectional");
        assertThatThrownBy(() -> FreeText.clean("note", "x\uD800y", 100, true)).hasMessageContaining("malformed");
        assertThatThrownBy(() -> FreeText.clean("note", "x￿", 100, true)).hasMessageContaining("noncharacter");
        assertThatThrownBy(() -> FreeText.clean("note", null, 100, true)).hasMessageContaining("required");
    }

    @Test
    void theErrorNamesTheFieldButNotTheValue() {
        try {
            FreeText.clean("note", "secret\u0007value", 100, true);
        } catch (InvalidInput e) {
            assertThat(e.getMessage()).isEqualTo("note: control character");
            assertThat(e.field()).isEqualTo("note");
            return;
        }
        throw new AssertionError("expected InvalidInput");
    }

    @Test
    void aHugeValueIsRejectedBeforeAnyWork() {
        String huge = "a".repeat(10_000_000);
        long t = System.nanoTime();
        assertThatThrownBy(() -> FreeText.clean("note", huge, 500, true)).hasMessageContaining("too long");
        assertThat((System.nanoTime() - t) / 1_000_000).isLessThan(50);
    }
}
