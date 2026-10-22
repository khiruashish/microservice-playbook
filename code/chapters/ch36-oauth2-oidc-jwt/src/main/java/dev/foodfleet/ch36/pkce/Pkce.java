package dev.foodfleet.ch36.pkce;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Pattern;

/**
 * Proof Key for Code Exchange (RFC 7636), required for every authorization code flow in OAuth 2.1: the client
 * sends a hash of a one-time secret with the authorization request and the secret itself with the code, so a
 * code intercepted on the way back (a malicious app registered for the same redirect scheme, a leaky log) is
 * useless without the secret.
 */
public final class Pkce {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern VERIFIER = Pattern.compile("[A-Za-z0-9\\-._~]{43,128}");
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

    private Pkce() {
    }

    /** 32 random bytes, base64url: 43 characters, 256 bits of entropy. */
    public static String newVerifier() {
        byte[] b = new byte[32];
        RANDOM.nextBytes(b);
        return B64.encodeToString(b);
    }

    /** code_challenge_method=S256 (plain is not allowed). */
    public static String challenge(String verifier) {
        if (!VERIFIER.matcher(verifier).matches()) {
            throw new IllegalArgumentException("a code verifier is 43-128 unreserved characters");
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return B64.encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static boolean matches(String verifier, String challenge) {
        if (verifier == null || challenge == null || !VERIFIER.matcher(verifier).matches()) {
            return false;
        }
        return MessageDigest.isEqual(challenge(verifier).getBytes(StandardCharsets.US_ASCII),
                challenge.getBytes(StandardCharsets.US_ASCII));
    }
}
