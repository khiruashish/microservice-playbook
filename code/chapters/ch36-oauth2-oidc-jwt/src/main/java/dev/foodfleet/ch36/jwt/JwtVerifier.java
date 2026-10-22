package dev.foodfleet.ch36.jwt;

import dev.foodfleet.ch36.json.Json;
import dev.foodfleet.ch36.jwt.JwtRejected.Reason;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Verifies JWT access tokens as a resource server must (RFC 8725 JWT best practices, RFC 9068 access tokens):
 * the algorithm comes from the verifier's allowlist, never from the token alone; the key is found by kid in the
 * issuer's key set and must be meant for that algorithm; then issuer, audience, type and time are checked. Any
 * failure is a rejection with a reason; nothing is partially trusted.
 */
public final class JwtVerifier {

    private static final Base64.Decoder B64 = Base64.getUrlDecoder();
    private static final int MAX_LENGTH = 8 * 1024;

    private final String issuer;
    private final String audience;
    private final Set<String> algorithms;
    private final Function<String, Optional<Jwk>> keys;
    private final Clock clock;
    private final Duration skew;

    public JwtVerifier(String issuer, String audience, Set<String> algorithms, Function<String, Optional<Jwk>> keys,
                       Clock clock, Duration skew) {
        if (algorithms.isEmpty() || algorithms.stream().anyMatch(a -> !a.equals("RS256") && !a.equals("ES256"))) {
            throw new IllegalArgumentException("only asymmetric algorithms (RS256, ES256) may be allowed");
        }
        this.issuer = issuer;
        this.audience = audience;
        this.algorithms = Set.copyOf(algorithms);
        this.keys = keys;
        this.clock = clock;
        this.skew = skew;
    }

    public VerifiedJwt verify(String token) {
        if (token == null || token.isEmpty()) {
            throw new JwtRejected(Reason.MALFORMED);
        }
        if (token.length() > MAX_LENGTH) {
            throw new JwtRejected(Reason.TOO_LARGE);
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3 || parts[2].isEmpty()) {
            throw new JwtRejected(Reason.MALFORMED);
        }
        Map<String, Object> header = json(parts[0]);
        Object alg = header.get("alg");
        if (!(alg instanceof String a) || !algorithms.contains(a)) {
            throw new JwtRejected(Reason.ALGORITHM_NOT_ALLOWED);
        }
        if (!"at+jwt".equalsIgnoreCase(String.valueOf(header.get("typ")))
                && !"application/at+jwt".equalsIgnoreCase(String.valueOf(header.get("typ")))) {
            throw new JwtRejected(Reason.WRONG_TYPE);                   // an ID token is not an access token
        }
        if (header.containsKey("crit")) {
            throw new JwtRejected(Reason.CRITICAL_HEADER);              // no extensions are understood here
        }
        Object kid = header.get("kid");
        Jwk jwk = kid instanceof String k ? keys.apply(k).orElseThrow(() -> new JwtRejected(Reason.UNKNOWN_KEY))
                : null;
        if (jwk == null) {
            throw new JwtRejected(Reason.UNKNOWN_KEY);
        }
        boolean keyFits = jwk.alg().equals(alg) && (alg.equals("RS256") ? jwk.key() instanceof RSAPublicKey
                : jwk.key() instanceof ECPublicKey);
        if (!keyFits) {
            throw new JwtRejected(Reason.KEY_ALGORITHM_MISMATCH);
        }
        try {
            Signature s = Signature.getInstance(JwtSigner.javaAlgorithm((String) alg));
            s.initVerify(jwk.key());
            s.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
            if (!s.verify(B64.decode(parts[2]))) {
                throw new JwtRejected(Reason.BAD_SIGNATURE);
            }
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new JwtRejected(Reason.BAD_SIGNATURE);
        }
        Map<String, Object> claims = json(parts[1]);            // only now: the claims are signed by the issuer
        if (!issuer.equals(claims.get("iss"))) {
            throw new JwtRejected(Reason.WRONG_ISSUER);
        }
        Object aud = claims.get("aud");
        boolean forUs = aud instanceof String s ? s.equals(audience) : aud instanceof List<?> l && l.contains(audience);
        if (!forUs) {
            throw new JwtRejected(Reason.WRONG_AUDIENCE);
        }
        Instant now = clock.instant();
        Instant exp = time(claims, "exp").orElseThrow(() -> new JwtRejected(Reason.MISSING_CLAIM));
        if (!now.isBefore(exp.plus(skew))) {
            throw new JwtRejected(Reason.EXPIRED);
        }
        Optional<Instant> nbf = time(claims, "nbf");
        if (nbf.isPresent() && now.isBefore(nbf.get().minus(skew))) {
            throw new JwtRejected(Reason.NOT_YET_VALID);
        }
        Optional<Instant> iat = time(claims, "iat");
        if (iat.isPresent() && iat.get().isAfter(now.plus(skew))) {
            throw new JwtRejected(Reason.ISSUED_IN_FUTURE);
        }
        Object sub = claims.get("sub");
        if (!(sub instanceof String subject)) {
            throw new JwtRejected(Reason.MISSING_CLAIM);
        }
        Set<String> scopes = new LinkedHashSet<>();
        if (claims.get("scope") instanceof String sc) {
            for (String x : sc.trim().split("\\s+")) {
                if (!x.isEmpty()) {
                    scopes.add(x);
                }
            }
        }
        return new VerifiedJwt(subject, (String) claims.get("client_id"), Set.copyOf(scopes), exp, (String) kid,
                Map.copyOf(withoutNulls(claims)));
    }

    private static Map<String, Object> json(String part) {
        try {
            return Json.object(new String(B64.decode(part), StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            throw new JwtRejected(Reason.MALFORMED);
        }
    }

    private static Optional<Instant> time(Map<String, Object> claims, String name) {
        Object v = claims.get(name);
        if (v == null) {
            return Optional.empty();
        }
        if (v instanceof Long l) {
            return Optional.of(Instant.ofEpochSecond(l));
        }
        throw new JwtRejected(Reason.MALFORMED);
    }

    private static Map<String, Object> withoutNulls(Map<String, Object> m) {
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        m.forEach((k, v) -> {
            if (v != null) {
                out.put(k, v);
            }
        });
        return out;
    }
}
