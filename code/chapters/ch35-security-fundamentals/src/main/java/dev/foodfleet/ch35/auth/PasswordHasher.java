package dev.foodfleet.ch35.auth;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Password storage with PBKDF2-HMAC-SHA256 at OWASP's minimum of 600,000 iterations, a random salt per password,
 * and the parameters stored with the hash so they can be raised later. Measured on one CPU core: 11.7-13.0 million
 * unsalted SHA-256 computations a second against 7.0 of these (143-144 ms each), so a stolen table costs an
 * attacker 1.7-1.9 million times more work per guess. (Spring Security's DelegatingPasswordEncoder does the same
 * job with Argon2, bcrypt or PBKDF2; chapter 37.)
 */
public final class PasswordHasher {

    public static final int ITERATIONS = 600_000;
    private static final SecureRandom RANDOM = new SecureRandom();
    private final int iterations;

    public PasswordHasher() {
        this(ITERATIONS);
    }

    public PasswordHasher(int iterations) {
        this.iterations = iterations;
    }

    /** {@code pbkdf2-sha256$iterations$salt$hash}. */
    public String hash(char[] password) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return "pbkdf2-sha256$" + iterations + "$" + b64(salt) + "$" + b64(derive(password, salt, iterations));
    }

    public boolean matches(char[] password, String stored) {
        String[] p = stored.split("\\$");
        if (p.length != 4 || !p[0].equals("pbkdf2-sha256")) {
            return false;
        }
        byte[] expected = Base64.getDecoder().decode(p[3]);
        byte[] actual = derive(password, Base64.getDecoder().decode(p[2]), Integer.parseInt(p[1]));
        return MessageDigest.isEqual(expected, actual);
    }

    /** Hashes made with fewer iterations than today's setting should be replaced at the next successful login. */
    public boolean needsRehash(String stored) {
        String[] p = stored.split("\\$");
        return p.length != 4 || Integer.parseInt(p[1]) < iterations;
    }

    static byte[] derive(char[] password, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, 256);
            try {
                return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            } finally {
                spec.clearPassword();
            }
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String b64(byte[] b) {
        return Base64.getEncoder().encodeToString(b);
    }
}
