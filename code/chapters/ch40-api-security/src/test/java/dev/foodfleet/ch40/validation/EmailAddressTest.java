package dev.foodfleet.ch40.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class EmailAddressTest {

    @Test
    void acceptsOrdinaryAddressesAndLowerCasesTheDomain() {
        assertThat(EmailAddress.parse("email", "asha.rao+food@Example.IN")).isEqualTo("asha.rao+food@example.in");
        assertThat(EmailAddress.parse("email", "o'brien@mail.co.uk")).isEqualTo("o'brien@mail.co.uk");
    }

    @Test
    void rejectsMalformedAddresses() {
        for (String bad : new String[] {"", "plain", "@example.in", "a@", "a@b", "a..b@example.in", "a@-x.in",
                "a@x_y.in", "a b@example.in", "a@example..in", "x".repeat(65) + "@example.in"}) {
            assertThatThrownBy(() -> EmailAddress.parse("email", bad)).isInstanceOf(InvalidInput.class);
        }
    }

    @Test
    void takesLinearTimeOnInputThatBreaksBacktrackingPatterns() {
        String evil = "a".repeat(60) + "!@" + "a".repeat(60);
        long t = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            try {
                EmailAddress.parse("email", evil);
            } catch (InvalidInput expected) {
                // rejected, quickly
            }
        }
        assertThat((System.nanoTime() - t) / 1_000_000).isLessThan(500);
    }
}
