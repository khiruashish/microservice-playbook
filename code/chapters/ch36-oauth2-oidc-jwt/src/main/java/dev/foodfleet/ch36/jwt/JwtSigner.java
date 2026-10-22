package dev.foodfleet.ch36.jwt;

import dev.foodfleet.ch36.json.Json;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Signs JWT access tokens (RFC 9068 profile: typ at+jwt) the way an authorization server does. Only the
 * authorization server holds the private key; resource servers get the public half from its JWKS.
 */
public final class JwtSigner {

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private final String kid;
    private final String alg;
    private final KeyPair keys;

    public JwtSigner(String kid, String alg, KeyPair keys) {
        this.kid = kid;
        this.alg = alg;
        this.keys = keys;
    }

    public static JwtSigner generate(String kid, String alg) {
        try {
            KeyPairGenerator g;
            if (alg.equals("RS256")) {
                g = KeyPairGenerator.getInstance("RSA");
                g.initialize(2048);
            } else if (alg.equals("ES256")) {
                g = KeyPairGenerator.getInstance("EC");
                g.initialize(new ECGenParameterSpec("secp256r1"));
            } else {
                throw new IllegalArgumentException("unsupported " + alg);
            }
            return new JwtSigner(kid, alg, g.generateKeyPair());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public Jwk publicJwk() {
        return new Jwk(kid, alg, keys.getPublic());
    }

    public String sign(Map<String, Object> claims) {
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", alg);
        header.put("typ", "at+jwt");
        header.put("kid", kid);
        return sign(header, claims);
    }

    /** Any header, signed with this key: for tests of what a verifier must refuse. */
    String sign(Map<String, Object> header, Map<String, Object> claims) {
        String input = B64.encodeToString(Json.write(header).getBytes(StandardCharsets.UTF_8)) + "."
                + B64.encodeToString(Json.write(claims).getBytes(StandardCharsets.UTF_8));
        try {
            Signature s = Signature.getInstance(javaAlgorithm(alg));
            s.initSign(keys.getPrivate());
            s.update(input.getBytes(StandardCharsets.US_ASCII));
            return input + "." + B64.encodeToString(s.sign());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** JWS ES256 signatures are R||S (64 bytes), which the JDK calls the P1363 format. */
    static String javaAlgorithm(String alg) {
        return switch (alg) {
            case "RS256" -> "SHA256withRSA";
            case "ES256" -> "SHA256withECDSAinP1363Format";
            default -> throw new IllegalArgumentException("unsupported " + alg);
        };
    }
}
