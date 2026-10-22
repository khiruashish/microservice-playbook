package dev.foodfleet.ch36.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import dev.foodfleet.ch36.jwt.JwtRejected.Reason;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class JwtVerifierTest {

    static final Instant NOW = Instant.parse("2025-04-14T10:00:00Z");
    static final String ISS = "https://id.foodfleet.example";
    final JwtSigner es = JwtSigner.generate("es-1", "ES256");
    final JwtSigner rs = JwtSigner.generate("rs-1", "RS256");
    final Map<String, Jwk> keys = Map.of("es-1", es.publicJwk(), "rs-1", rs.publicJwk());
    final JwtVerifier verifier = verifierAt(NOW);

    JwtVerifier verifierAt(Instant now) {
        return new JwtVerifier(ISS, "order-service", Set.of("ES256", "RS256"), kid -> Optional.ofNullable(keys.get(kid)),
                Clock.fixed(now, ZoneOffset.UTC), Duration.ofSeconds(30));
    }

    static Map<String, Object> claims() {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("iss", ISS);
        c.put("sub", "c01");
        c.put("aud", "order-service");
        c.put("client_id", "customer-app");
        c.put("scope", "orders:read orders:write");
        c.put("iat", NOW.getEpochSecond() - 60);
        c.put("exp", NOW.getEpochSecond() + 240);
        return c;
    }

    static Map<String, Object> header(String alg, String kid, String typ) {
        Map<String, Object> h = new LinkedHashMap<>();
        h.put("alg", alg);
        h.put("typ", typ);
        h.put("kid", kid);
        return h;
    }

    Reason rejection(String token) {
        try {
            verifier.verify(token);
            return null;
        } catch (JwtRejected e) {
            return e.reason();
        }
    }

    @Test
    void validTokensFromBothKeyTypesVerify() {
        VerifiedJwt t = verifier.verify(es.sign(claims()));
        assertThat(t.subject()).isEqualTo("c01");
        assertThat(t.scopes()).isEqualTo(Set.of("orders:read", "orders:write"));
        assertThat(verifier.verify(rs.sign(claims())).kid()).isEqualTo("rs-1");
    }

    @Test
    void timeIsCheckedWithASmallClockSkew() {
        String token = es.sign(claims());
        assertThat(verifierAt(NOW.plusSeconds(260)).verify(token).subject()).isEqualTo("c01");   // within 30 s skew
        try {
            verifierAt(NOW.plusSeconds(271)).verify(token);
            throw new AssertionError("expected expiry");
        } catch (JwtRejected e) {
            assertThat(e.reason()).isEqualTo(Reason.EXPIRED);
        }
        Map<String, Object> early = claims();
        early.put("nbf", NOW.getEpochSecond() + 120);
        assertThat(rejection(es.sign(early))).isEqualTo(Reason.NOT_YET_VALID);
    }

    @Test
    void tokensForSomeoneElseAreRefused() {
        Map<String, Object> c = claims();
        c.put("aud", "payment-service");
        assertThat(rejection(es.sign(c))).isEqualTo(Reason.WRONG_AUDIENCE);
        c = claims();
        c.put("iss", "https://other-issuer.example");
        assertThat(rejection(es.sign(c))).isEqualTo(Reason.WRONG_ISSUER);
    }

    @Test
    void theAlgorithmComesFromTheAllowlistAndMustMatchTheKey() {
        assertThat(rejection(es.sign(header("none", "es-1", "at+jwt"), claims()))).isEqualTo(Reason.ALGORITHM_NOT_ALLOWED);
        assertThat(rejection(es.sign(header("HS256", "es-1", "at+jwt"), claims()))).isEqualTo(Reason.ALGORITHM_NOT_ALLOWED);
        assertThat(rejection(es.sign(header("RS256", "es-1", "at+jwt"), claims())))
                .isEqualTo(Reason.KEY_ALGORITHM_MISMATCH);
        assertThat(rejection(es.sign(header("ES256", "unknown", "at+jwt"), claims()))).isEqualTo(Reason.UNKNOWN_KEY);
    }

    @Test
    void anIdTokenOrAnUnknownExtensionIsNotAnAccessToken() {
        assertThat(rejection(es.sign(header("ES256", "es-1", "JWT"), claims()))).isEqualTo(Reason.WRONG_TYPE);
        Map<String, Object> h = header("ES256", "es-1", "at+jwt");
        h.put("crit", java.util.List.of("exp-ext"));
        assertThat(rejection(es.sign(h, claims()))).isEqualTo(Reason.CRITICAL_HEADER);
    }

    @Test
    void anyChangeToTheSignedPartsBreaksTheSignature() {
        String token = es.sign(claims());
        String[] p = token.split("\\.");
        Map<String, Object> more = claims();
        more.put("scope", "orders:read orders:write admin");
        String forged = es.sign(more).split("\\.")[1];
        assertThat(rejection(p[0] + "." + forged + "." + p[2])).isEqualTo(Reason.BAD_SIGNATURE);
        assertThat(rejection(p[0] + "." + p[1] + ".")).isEqualTo(Reason.MALFORMED);
        assertThat(rejection(token + "." + p[2])).isEqualTo(Reason.MALFORMED);
        assertThat(rejection("x".repeat(9000))).isEqualTo(Reason.TOO_LARGE);
    }

    @Test
    void requiredClaimsMustBePresentAndPlausible() {
        Map<String, Object> c = claims();
        c.remove("sub");
        assertThat(rejection(es.sign(c))).isEqualTo(Reason.MISSING_CLAIM);
        c = claims();
        c.remove("exp");
        assertThat(rejection(es.sign(c))).isEqualTo(Reason.MISSING_CLAIM);
        c = claims();
        c.put("iat", NOW.getEpochSecond() + 600);
        assertThat(rejection(es.sign(c))).isEqualTo(Reason.ISSUED_IN_FUTURE);
    }

    @Test
    void symmetricAlgorithmsCannotEvenBeConfigured() {
        try {
            new JwtVerifier(ISS, "order-service", Set.of("HS256"), kid -> Optional.empty(), Clock.systemUTC(),
                    Duration.ZERO);
            throw new AssertionError("expected a refusal");
        } catch (IllegalArgumentException expected) {
            assertThat(expected.getMessage()).contains("asymmetric");
        }
    }
}
