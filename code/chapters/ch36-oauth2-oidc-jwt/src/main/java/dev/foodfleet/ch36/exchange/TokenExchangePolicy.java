package dev.foodfleet.ch36.exchange;

import dev.foodfleet.ch36.jwt.VerifiedJwt;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The authorization server's rules for OAuth 2.0 Token Exchange (RFC 8693), used when a service calls another on
 * a user's behalf. Instead of relaying the user's token unchanged (which the next service could replay anywhere
 * the token is accepted), the calling service exchanges it for a new token: for one audience only, with only the
 * scopes that audience needs and the user had, for minutes, and with an "act" claim naming the service acting.
 */
public final class TokenExchangePolicy {

    /** What a client may ask for: target audience to the most scopes it may receive there. */
    public record ClientRules(String clientId, Map<String, Set<String>> audiences) {
    }

    public record Grant(Map<String, Object> claims) {
    }

    public static final class Refused extends RuntimeException {
        private final String error;

        public Refused(String error, String description) {
            super(description, null, false, false);
            this.error = error;
        }

        /** The OAuth error code: invalid_request, invalid_target, invalid_scope, ... */
        public String error() {
            return error;
        }
    }

    private final Map<String, ClientRules> clients;
    private final Duration maxLifetime;

    public TokenExchangePolicy(List<ClientRules> clients, Duration maxLifetime) {
        Map<String, ClientRules> m = new LinkedHashMap<>();
        clients.forEach(c -> m.put(c.clientId(), c));
        this.clients = Map.copyOf(m);
        this.maxLifetime = maxLifetime;
    }

    /**
     * @param client the authenticated client asking for the exchange
     * @param subject the user's token, already verified by the authorization server
     */
    public Grant exchange(String client, VerifiedJwt subject, String audience, Set<String> requestedScopes,
                          String issuer, Instant now) {
        ClientRules rules = clients.get(client);
        if (rules == null) {
            throw new Refused("unauthorized_client", "client may not exchange tokens");
        }
        Object aud = subject.claims().get("aud");
        boolean issuedToClient = aud instanceof String s ? s.equals(client) : aud instanceof List<?> l && l.contains(client);
        if (!issuedToClient) {
            throw new Refused("invalid_grant", "the subject token was not issued to this client");
        }
        Set<String> allowedThere = rules.audiences().get(audience);
        if (allowedThere == null) {
            throw new Refused("invalid_target", "client may not obtain tokens for " + audience);
        }
        Set<String> granted = new LinkedHashSet<>(requestedScopes.isEmpty() ? allowedThere : requestedScopes);
        granted.retainAll(allowedThere);
        granted.retainAll(subject.scopes());
        if (granted.isEmpty()) {
            throw new Refused("invalid_scope", "none of the requested scopes may be granted");
        }
        Instant exp = subject.expiresAt().isBefore(now.plus(maxLifetime)) ? subject.expiresAt() : now.plus(maxLifetime);
        Map<String, Object> act = new LinkedHashMap<>();
        act.put("sub", client);
        if (subject.claims().get("act") != null) {
            act.put("act", subject.claims().get("act"));                 // keep the whole delegation chain
        }
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", issuer);
        claims.put("sub", subject.subject());
        claims.put("aud", audience);
        claims.put("client_id", client);
        claims.put("scope", String.join(" ", granted));
        claims.put("act", act);
        claims.put("iat", now.getEpochSecond());
        claims.put("exp", exp.getEpochSecond());
        return new Grant(claims);
    }
}
