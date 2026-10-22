package dev.foodfleet.ch36.jwt;

import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/** A public verification key from a JSON Web Key Set (RFC 7517): RSA for RS256, P-256 for ES256. */
public record Jwk(String kid, String alg, PublicKey key) {

    static final Base64.Decoder B64 = Base64.getUrlDecoder();
    static final Base64.Encoder B64E = Base64.getUrlEncoder().withoutPadding();

    public static Jwk fromJson(Map<String, Object> j) {
        String kid = (String) j.get("kid");
        String alg = (String) j.get("alg");
        if (kid == null || alg == null) {
            throw new IllegalArgumentException("keys must name their kid and alg");
        }
        try {
            return switch (String.valueOf(j.get("kty"))) {
                case "RSA" -> new Jwk(kid, alg, KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(
                        unsigned((String) j.get("n")), unsigned((String) j.get("e")))));
                case "EC" -> {
                    if (!"P-256".equals(j.get("crv"))) {
                        throw new IllegalArgumentException("only P-256");
                    }
                    ECPoint w = new ECPoint(unsigned((String) j.get("x")), unsigned((String) j.get("y")));
                    yield new Jwk(kid, alg, KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(w, p256())));
                }
                default -> throw new IllegalArgumentException("unsupported kty " + j.get("kty"));
            };
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("invalid key " + kid, e);
        }
    }

    public Map<String, Object> toJson() {
        Map<String, Object> j = new LinkedHashMap<>();
        j.put("kid", kid);
        j.put("alg", alg);
        j.put("use", "sig");
        if (key instanceof RSAPublicKey rsa) {
            j.put("kty", "RSA");
            j.put("n", B64E.encodeToString(bytes(rsa.getModulus(), 0)));
            j.put("e", B64E.encodeToString(bytes(rsa.getPublicExponent(), 0)));
        } else if (key instanceof ECPublicKey ec) {
            j.put("kty", "EC");
            j.put("crv", "P-256");
            j.put("x", B64E.encodeToString(bytes(ec.getW().getAffineX(), 32)));
            j.put("y", B64E.encodeToString(bytes(ec.getW().getAffineY(), 32)));
        }
        return j;
    }

    static ECParameterSpec p256() throws GeneralSecurityException {
        AlgorithmParameters p = AlgorithmParameters.getInstance("EC");
        p.init(new ECGenParameterSpec("secp256r1"));
        return p.getParameterSpec(ECParameterSpec.class);
    }

    private static BigInteger unsigned(String b64) {
        return new BigInteger(1, B64.decode(b64));
    }

    private static byte[] bytes(BigInteger v, int length) {
        byte[] b = v.toByteArray();
        if (b.length > 1 && b[0] == 0) {
            b = Arrays.copyOfRange(b, 1, b.length);
        }
        if (length > b.length) {
            byte[] padded = new byte[length];
            System.arraycopy(b, 0, padded, length - b.length, b.length);
            return padded;
        }
        return b;
    }
}
