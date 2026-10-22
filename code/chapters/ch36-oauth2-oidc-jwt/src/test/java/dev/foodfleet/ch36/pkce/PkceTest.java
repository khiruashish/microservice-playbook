package dev.foodfleet.ch36.pkce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PkceTest {

    @Test
    void matchesTheRfc7636Example() {
        assertThat(Pkce.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
                .isEqualTo("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM");
    }

    @Test
    void aFreshVerifierMatchesOnlyItsOwnChallenge() {
        String v = Pkce.newVerifier();
        assertThat(v.length()).isEqualTo(43);
        assertThat(Pkce.matches(v, Pkce.challenge(v))).isTrue();
        assertThat(Pkce.matches(Pkce.newVerifier(), Pkce.challenge(v))).isFalse();
        assertThat(Pkce.matches(null, Pkce.challenge(v))).isFalse();
    }

    @Test
    void shortVerifiersAreRefused() {
        assertThatThrownBy(() -> Pkce.challenge("too-short")).isInstanceOf(IllegalArgumentException.class);
    }
}
