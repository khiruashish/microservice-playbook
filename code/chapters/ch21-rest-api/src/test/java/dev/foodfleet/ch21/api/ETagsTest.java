package dev.foodfleet.ch21.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ETagsTest {

    @Test
    void strongETagsCarryTheVersion() {
        assertThat(ETags.of(3)).isEqualTo("\"3\"");
        assertThat(ETags.versionIn("\"3\"")).isEqualTo(3L);
        assertThat(ETags.versionIn(" \"7\", \"8\" ")).isEqualTo(7L);
    }

    @Test
    void weakOrMalformedETagsNeverMatch() {
        assertThat(ETags.versionIn("W/\"3\"")).isEqualTo(-1L);
        assertThat(ETags.versionIn("3")).isEqualTo(-1L);
        assertThat(ETags.versionIn("\"three\"")).isEqualTo(-1L);
        assertThat(ETags.versionIn("\"\"")).isEqualTo(-1L);
    }
}
