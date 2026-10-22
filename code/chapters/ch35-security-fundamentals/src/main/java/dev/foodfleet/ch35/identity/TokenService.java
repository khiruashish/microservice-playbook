package dev.foodfleet.ch35.identity;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * A deliberately small signed token (HMAC-SHA256) so this chapter's examples have real authentication without a
 * framework: {@code base64(subject|roles|restaurants|expiry).base64(signature)}. Chapter 36 replaces it with
 * OAuth 2.1 access tokens (JWTs) and chapter 37 with Spring Security's resource server. What carries over: verify
 * the signature with a constant-time comparison, reject expired tokens, take identity only from a verified token.
 */
public final class TokenService {

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();
    private final SecretKeySpec key;
    private final Clock clock;
    private final Duration lifetime;

    public TokenService(byte[] secret, Clock clock, Duration lifetime) {
        if (secret.length < 32) {
            throw new IllegalArgumentException("an HMAC-SHA256 key needs at least 256 bits");
        }
        this.key = new SecretKeySpec(secret.clone(), "HmacSHA256");
        this.clock = clock;
        this.lifetime = lifetime;
    }

    public String issue(Caller caller) {
        String body = caller.id() + "|"
                + caller.roles().stream().map(Enum::name).sorted().collect(Collectors.joining(",")) + "|"
                + caller.restaurantIds().stream().sorted().map(String::valueOf).collect(Collectors.joining(",")) + "|"
                + clock.instant().plus(lifetime).getEpochSecond();
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        return B64.encodeToString(payload) + "." + B64.encodeToString(sign(payload));
    }

    /** The caller, if the token is well-formed, correctly signed and not expired; empty otherwise. */
    public Optional<Caller> verify(String token) {
        if (token == null) {
            return Optional.empty();
        }
        int dot = token.indexOf('.');
        if (dot < 1 || dot != token.lastIndexOf('.')) {
            return Optional.empty();
        }
        try {
            byte[] payload = B64D.decode(token.substring(0, dot));
            byte[] signature = B64D.decode(token.substring(dot + 1));
            if (!MessageDigest.isEqual(sign(payload), signature)) {        // constant time
                return Optional.empty();
            }
            String[] parts = new String(payload, StandardCharsets.UTF_8).split("\\|", -1);
            if (parts.length != 4 || Long.parseLong(parts[3]) <= clock.instant().getEpochSecond()) {
                return Optional.empty();
            }
            Set<Role> roles = EnumSet.noneOf(Role.class);
            for (String r : parts[1].split(",")) {
                if (!r.isEmpty()) {
                    roles.add(Role.valueOf(r));
                }
            }
            Set<Long> restaurants = new HashSet<>();
            for (String r : parts[2].split(",")) {
                if (!r.isEmpty()) {
                    restaurants.add(Long.parseLong(r));
                }
            }
            return Optional.of(new Caller(parts[0], roles, restaurants));
        } catch (IllegalArgumentException e) {                           // bad base64, number or role
            return Optional.empty();
        }
    }

    private byte[] sign(byte[] payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return mac.doFinal(payload);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public String toString() {
        return "TokenService[key=" + "*".repeat(8) + ", lifetime=" + lifetime + "]";   // never log the key
    }
}
