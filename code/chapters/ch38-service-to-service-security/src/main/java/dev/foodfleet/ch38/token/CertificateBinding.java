package dev.foodfleet.ch38.token;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Map;

/**
 * RFC 8705 certificate-bound access tokens: the authorization server puts the SHA-256 thumbprint of the client's
 * certificate in {@code cnf.x5t#S256}; a resource server accepts the token only over a TLS connection on which the
 * client presented that certificate. A stolen token is then useless without the private key.
 * The decision table is the same as Spring Security 7.1's {@code X509CertificateThumbprintValidator}, which every
 * Spring Boot resource server includes: no {@code cnf} → an ordinary bearer token, accepted; {@code cnf} but no
 * client certificate on the request → rejected; thumbprint mismatch → rejected.
 */
public final class CertificateBinding {

    public record Result(boolean accepted, String reason) {
    }

    private CertificateBinding() {
    }

    public static String thumbprint(X509Certificate cert) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(cert.getEncoded());
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** {@code cnf} claim for a token issued to the holder of {@code cert}. */
    public static Map<String, Object> confirmation(X509Certificate cert) {
        return Map.of("x5t#S256", thumbprint(cert));
    }

    public static Result check(Map<String, Object> claims, X509Certificate peerCertificate) {
        Object cnf = claims.get("cnf");
        Object expected = cnf instanceof Map<?, ?> m ? m.get("x5t#S256") : null;
        if (expected == null) {
            return new Result(true, "bearer token: no certificate binding");
        }
        if (peerCertificate == null) {
            return new Result(false, "bound token but no client certificate on this request");
        }
        if (!expected.equals(thumbprint(peerCertificate))) {
            return new Result(false, "bound to a different certificate");
        }
        return new Result(true, "bound to this client's certificate");
    }
}
