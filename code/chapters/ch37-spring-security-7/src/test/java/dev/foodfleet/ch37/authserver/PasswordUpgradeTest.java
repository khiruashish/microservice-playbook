package dev.foodfleet.ch37.authserver;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;

/** Spring Security's own encoders: old hashes keep working and are flagged for upgrade. */
class PasswordUpgradeTest {

    final PasswordEncoder encoder = new Users().passwordEncoder();

    @Test
    void newHashesAreBcryptAtStrength12() {
        String hash = encoder.encode("dosa1234");
        assertThat(hash).startsWith("{bcrypt}$2a$12$");
        assertThat(encoder.matches("dosa1234", hash)).isTrue();
        assertThat(encoder.upgradeEncoding(hash)).isFalse();
    }

    @Test
    void weakerOrOlderHashesStillMatchAndAskToBeUpgraded() {
        String bcrypt10 = "{bcrypt}" + new BCryptPasswordEncoder(10).encode("dosa1234");
        String pbkdf2 = "{pbkdf2@SpringSecurity_v5_8}" + Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8().encode("dosa1234");
        for (String old : new String[] {bcrypt10, pbkdf2}) {
            assertThat(encoder.matches("dosa1234", old)).isTrue();
            assertThat(encoder.upgradeEncoding(old)).isTrue();
        }
    }

    @Test
    void unknownSchemesDoNotMatch() {
        PasswordEncoder strict = new DelegatingPasswordEncoder("bcrypt", Map.of("bcrypt", new BCryptPasswordEncoder()));
        try {
            strict.matches("dosa1234", "{noop}dosa1234");
            throw new AssertionError("expected an exception for an unmapped id");
        } catch (IllegalArgumentException expected) {
            assertThat(expected.getMessage()).contains("noop");
        }
    }
}
