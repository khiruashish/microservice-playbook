package dev.foodfleet.ch35.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TokenServiceTest {

    static final byte[] KEY = "0123456789abcdef0123456789abcdef".getBytes();
    static final Instant NOW = Instant.parse("2025-04-14T10:00:00Z");
    final Caller asha = new Caller("c01", Set.of(Role.CUSTOMER), Set.of());

    TokenService at(Instant now) {
        return new TokenService(KEY, Clock.fixed(now, ZoneOffset.UTC), Duration.ofMinutes(15));
    }

    @Test
    void aTokenItIssuedVerifiesToTheSameCaller() {
        var tokens = at(NOW);
        assertThat(tokens.verify(tokens.issue(asha))).contains(asha);
    }

    @Test
    void anExpiredTokenIsRejected() {
        String token = at(NOW).issue(asha);
        assertThat(at(NOW.plus(Duration.ofMinutes(16))).verify(token)).isEmpty();
    }

    @Test
    void aTokenSignedWithAnotherKeyIsRejected() {
        var other = new TokenService("ffffffffffffffffffffffffffffffff".getBytes(), Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofMinutes(15));
        assertThat(at(NOW).verify(other.issue(asha))).isEmpty();
    }

    @Test
    void changingAnyPartOfTheTokenInvalidatesIt() {
        String token = at(NOW).issue(asha);
        String body = token.substring(0, token.indexOf('.'));
        String sig = token.substring(token.indexOf('.') + 1);
        String alteredBody = (body.charAt(0) == 'A' ? 'B' : 'A') + body.substring(1);
        assertThat(at(NOW).verify(alteredBody + "." + sig)).isEmpty();
        assertThat(at(NOW).verify(body + ".")).isEmpty();
        assertThat(at(NOW).verify("not-a-token")).isEmpty();
        assertThat(at(NOW).verify(null)).isEmpty();
    }

    @Test
    void shortKeysAreRefused() {
        try {
            new TokenService("too-short".getBytes(), Clock.systemUTC(), Duration.ofMinutes(5));
            throw new AssertionError("expected a refusal");
        } catch (IllegalArgumentException expected) {
            assertThat(expected.getMessage()).contains("256 bits");
        }
    }
}
