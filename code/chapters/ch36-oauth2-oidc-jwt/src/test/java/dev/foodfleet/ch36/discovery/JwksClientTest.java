package dev.foodfleet.ch36.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import dev.foodfleet.ch36.json.Json;
import dev.foodfleet.ch36.jwt.JwtSigner;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JwksClientTest {

    static final class MutableClock extends Clock {
        Instant now = Instant.parse("2025-04-14T10:00:00Z");

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    final MutableClock clock = new MutableClock();
    final List<JwtSigner> published = new ArrayList<>(List.of(JwtSigner.generate("k1", "ES256")));
    final URI issuer = URI.create("https://id.foodfleet.example");
    final JwksClient client = new JwksClient(issuer, uri -> uri.getPath().endsWith("openid-configuration")
            ? Json.write(Map.of("issuer", issuer.toString(), "jwks_uri", "https://id.foodfleet.example/jwks"))
            : Json.write(Map.of("keys", published.stream().map(s -> s.publicJwk().toJson()).toList())),
            clock, Duration.ofMinutes(10), Duration.ofSeconds(30));

    @Test
    void aRotatedKeyIsPickedUpOnFirstSight() {
        assertThat(client.key("k1")).isPresent();
        published.addFirst(JwtSigner.generate("k2", "ES256"));            // the issuer rotates
        clock.now = clock.now.plusSeconds(31);
        assertThat(client.key("k2")).isPresent();
        assertThat(client.fetches()).isEqualTo(3L);                       // discovery, JWKS, JWKS again
    }

    @Test
    void madeUpKidsCannotMakeItHammerTheIssuer() {
        client.key("k1");
        for (int i = 0; i < 1000; i++) {
            assertThat(client.key("bogus-" + i)).isEmpty();
        }
        assertThat(client.fetches()).isEqualTo(2L);                       // no refresh within 30 s
    }
}
