package dev.foodfleet.ch39.secret;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class SecretTest {

    @Test
    void theValueNeverAppearsByAccident() {
        Secret s = Secret.of("hunter2-db-password".toCharArray());
        assertThat(s.toString()).isEqualTo("Secret[****]");
        assertThat("connecting with " + s).isEqualTo("connecting with Secret[****]");
        assertThat(new IllegalStateException("bad " + s).getMessage()).isEqualTo("bad Secret[****]");
    }

    @Test
    void revealReturnsACopyTheCallerCanWipe() {
        Secret s = Secret.of("abc".getBytes(StandardCharsets.UTF_8));
        char[] copy = s.reveal();
        java.util.Arrays.fill(copy, 'x');
        assertThat(s.revealAsString()).isEqualTo("abc");
    }

    @Test
    void destroyWipesAndLaterUseFails() {
        Secret s = Secret.of("abc".toCharArray());
        s.destroy();
        assertThat(s.isDestroyed()).isTrue();
        assertThatThrownBy(s::reveal).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void comparisonIsByValueButNeverThroughEquals() {
        Secret a = Secret.of("same".toCharArray());
        Secret b = Secret.of("same".toCharArray());
        assertThat(a.matches("same".toCharArray())).isTrue();
        assertThat(a.matches("sam".toCharArray())).isFalse();
        assertThat(a.matches("same!".toCharArray())).isFalse();
        assertThat(a.equals(b)).isFalse();                 // not usable as a map key or in a Set by accident
    }

    @Test
    void utf8BytesDecodeCompletely() {
        Secret s = Secret.of("pässwört-€".getBytes(StandardCharsets.UTF_8));
        assertThat(s.revealAsString()).isEqualTo("pässwört-€");
        assertThat(s.matches("pässwört-€".toCharArray())).isTrue();
    }
}
