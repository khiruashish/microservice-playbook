package dev.foodfleet.ch36.jwt;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

/** A token that passed every check: signature, algorithm, issuer, audience, type and time. */
public record VerifiedJwt(String subject, String clientId, Set<String> scopes, Instant expiresAt, String kid,
                          Map<String, Object> claims) {

    public boolean hasScope(String scope) {
        return scopes.contains(scope);
    }
}
