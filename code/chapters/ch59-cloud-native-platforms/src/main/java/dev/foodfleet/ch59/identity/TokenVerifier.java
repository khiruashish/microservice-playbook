package dev.foodfleet.ch59.identity;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What a cloud's token service (AWS STS, Google's STS, Microsoft Entra ID) does with a Kubernetes service account token
 * before trusting it: verify the RS256 signature with the cluster's published keys (its OIDC discovery document and
 * JWKS), check the issuer, the audience and the expiry. Nothing here calls the cluster: the check is offline, which is
 * what lets any cloud trust any cluster, and also why a token outlives the pod it was issued to.
 */
public final class TokenVerifier {

    public record Claims(String issuer, List<String> audience, String subject, long issuedAt, long expiresAt, String pod) {
    }

    private final String issuer;
    private final Map<String, PublicKey> keys = new HashMap<>();

    /** {@code jwks}: the JSON at the cluster's {@code /openid/v1/jwks}. */
    public TokenVerifier(String issuer, String jwks) {
        this.issuer = issuer;
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> list = (List<Map<String, Object>>) Json.object(jwks).get("keys");
            for (Map<String, Object> k : list) {
                if ("RSA".equals(k.get("kty"))) {
                    BigInteger n = new BigInteger(1, Base64.getUrlDecoder().decode((String) k.get("n")));
                    BigInteger e = new BigInteger(1, Base64.getUrlDecoder().decode((String) k.get("e")));
                    keys.put((String) k.get("kid"), KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(n, e)));
                }
            }
        } catch (java.security.GeneralSecurityException ex) {
            throw new IllegalArgumentException("bad JWKS", ex);
        }
    }

    /** The token's claims if it's genuine, from this issuer, for this audience and not expired; otherwise an exception. */
    public Claims verify(String jwt, String audience, long nowEpochSeconds) {
        String[] parts = jwt.trim().split("\\.");
        if (parts.length != 3) {
            throw new SecurityException("not a JWT");
        }
        Map<String, Object> header = Json.object(decode(parts[0]));
        if (!"RS256".equals(header.get("alg"))) {
            throw new SecurityException("unsupported alg " + header.get("alg"));
        }
        PublicKey key = keys.get((String) header.get("kid"));
        if (key == null) {
            throw new SecurityException("unknown signing key " + header.get("kid"));
        }
        try {
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initVerify(key);
            sig.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
            if (!sig.verify(Base64.getUrlDecoder().decode(parts[2]))) {
                throw new SecurityException("bad signature");
            }
        } catch (java.security.GeneralSecurityException ex) {
            throw new SecurityException("bad signature", ex);
        }
        Map<String, Object> c = Json.object(decode(parts[1]));
        if (!issuer.equals(c.get("iss"))) {
            throw new SecurityException("wrong issuer " + c.get("iss"));
        }
        List<String> aud = c.get("aud") instanceof List<?> l ? l.stream().map(String::valueOf).toList() : List.of(String.valueOf(c.get("aud")));
        if (!aud.contains(audience)) {
            throw new SecurityException("wrong audience " + aud);
        }
        long exp = ((Number) c.get("exp")).longValue();
        if (nowEpochSeconds >= exp) {
            throw new SecurityException("expired " + (nowEpochSeconds - exp) + " s ago");
        }
        String pod = null;
        if (c.get("kubernetes.io") instanceof Map<?, ?> k8s && k8s.get("pod") instanceof Map<?, ?> p) {
            pod = String.valueOf(p.get("name"));
        }
        return new Claims((String) c.get("iss"), aud, (String) c.get("sub"), ((Number) c.get("iat")).longValue(), exp, pod);
    }

    static String decode(String b64url) {
        return new String(Base64.getUrlDecoder().decode(b64url), StandardCharsets.UTF_8);
    }
}
