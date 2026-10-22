package dev.foodfleet.ch36.authserver;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.foodfleet.ch36.exchange.TokenExchangePolicy;
import dev.foodfleet.ch36.json.Json;
import dev.foodfleet.ch36.jwt.Jwk;
import dev.foodfleet.ch36.jwt.JwtRejected;
import dev.foodfleet.ch36.jwt.JwtSigner;
import dev.foodfleet.ch36.jwt.JwtVerifier;
import dev.foodfleet.ch36.jwt.VerifiedJwt;
import dev.foodfleet.ch36.pkce.Pkce;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/**
 * A small OAuth 2.1 / OpenID Connect authorization server for this chapter's tests and measurements, JDK only:
 * discovery, JWKS with key rotation, the token endpoint (authorization code with PKCE, client credentials,
 * token exchange), introspection (RFC 7662) and revocation (RFC 7009). It has no login pages: {@link #authorize}
 * stands in for the user signing in and consenting. A teaching stand-in; production uses Spring Authorization
 * Server or Keycloak (chapter 37).
 */
public final class MiniAuthorizationServer implements AutoCloseable {

    public record Client(String id, String secretSha256, Set<String> redirectUris, Set<String> grants,
                         Set<String> scopes, String audience) {
    }

    private record PendingCode(String client, String redirectUri, String user, Set<String> scopes, String challenge,
                               Instant expires) {
    }

    private record Issued(String jti, Instant exp, String fromCode) {
    }

    public static final String TOKEN_EXCHANGE = "urn:ietf:params:oauth:grant-type:token-exchange";
    public static final String ACCESS_TOKEN_TYPE = "urn:ietf:params:oauth:token-type:access_token";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Clock clock;
    private final Duration accessTokenLifetime;
    private final Map<String, Client> clients = new ConcurrentHashMap<>();
    private final Map<String, PendingCode> codes = new ConcurrentHashMap<>();
    private final Map<String, String> usedCodes = new ConcurrentHashMap<>();
    private final Map<String, Issued> issued = new ConcurrentHashMap<>();
    private final Set<String> revoked = ConcurrentHashMap.newKeySet();
    private final TokenExchangePolicy exchangePolicy;
    private final List<JwtSigner> published = new ArrayList<>();       // newest first: next, active, previous
    private volatile JwtSigner active;
    private volatile String issuer = "http://127.0.0.1";
    private HttpServer server;

    public MiniAuthorizationServer(Clock clock, Duration accessTokenLifetime, TokenExchangePolicy exchangePolicy) {
        this.clock = clock;
        this.accessTokenLifetime = accessTokenLifetime;
        this.exchangePolicy = exchangePolicy;
        rotateKey();
    }

    public String issuer() {
        return issuer;
    }

    public void register(Client client) {
        clients.put(client.id(), client);
    }

    public static String sha256(String secret) {
        try {
            return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Sudden rotation: a new key, used at once. Resource servers that haven't seen it yet reject its tokens. */
    public synchronized void rotateKey() {
        publishNextKey();
        activateNewestKey();
    }

    /** Staged rotation, step 1: publish the next key in the JWKS without signing with it yet. */
    public synchronized void publishNextKey() {
        published.addFirst(JwtSigner.generate("k" + (published.size() + 1 + retired), "ES256"));
    }

    /**
     * Staged rotation, step 2 (after resource servers' caches have had time to refresh): sign with the newest key.
     * The previous key stays published until the tokens it signed have expired.
     */
    public synchronized void activateNewestKey() {
        active = published.getFirst();
        while (published.size() > 3) {
            published.removeLast();
            retired++;
        }
    }

    private int retired;

    public synchronized List<Jwk> publicKeys() {
        return published.stream().map(JwtSigner::publicJwk).toList();
    }

    public MiniAuthorizationServer start(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        issuer = "http://127.0.0.1:" + server.getAddress().getPort();
        server.createContext("/.well-known/openid-configuration", ex -> json(ex, 200, Map.of(
                "issuer", issuer, "jwks_uri", issuer + "/jwks", "token_endpoint", issuer + "/token",
                "introspection_endpoint", issuer + "/introspect", "revocation_endpoint", issuer + "/revoke",
                "grant_types_supported", List.of("authorization_code", "client_credentials", TOKEN_EXCHANGE),
                "code_challenge_methods_supported", List.of("S256"))));
        server.createContext("/jwks", ex -> json(ex, 200, Map.of("keys", publicKeys().stream().map(Jwk::toJson).toList())));
        server.createContext("/token", this::token);
        server.createContext("/introspect", this::introspect);
        server.createContext("/revoke", this::revoke);
        server.start();
        return this;
    }

    @Override
    public void close() {
        if (server != null) {
            server.stop(0);
        }
    }

    // ------------------------------------------------------------------ the user's side of the code flow

    /** What happens after the user signs in and consents: a one-time code bound to the client, redirect and PKCE. */
    public String authorize(String clientId, String redirectUri, String user, Set<String> scopes, String challenge,
                            String method) {
        Client c = clients.get(clientId);
        if (c == null || !c.grants().contains("authorization_code") || !c.redirectUris().contains(redirectUri)) {
            throw new IllegalArgumentException("unknown client or redirect URI not registered (exact match)");
        }
        if (!"S256".equals(method) || challenge == null) {
            throw new IllegalArgumentException("PKCE with S256 is required");
        }
        Set<String> granted = new LinkedHashSet<>(scopes);
        granted.retainAll(c.scopes());
        String code = random();
        codes.put(code, new PendingCode(clientId, redirectUri, user, granted, challenge,
                clock.instant().plusSeconds(60)));
        return code;
    }

    /** Issues an access token directly (tests and measurements). */
    public String issue(String subject, String clientId, String audience, Set<String> scopes, Duration lifetime) {
        return sign(subject, clientId, audience, scopes, lifetime, null, null);
    }

    // ------------------------------------------------------------------ endpoints

    private void token(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("POST")) {
            json(ex, 405, Map.of("error", "invalid_request"));
            return;
        }
        Optional<Client> client = authenticate(ex);
        if (client.isEmpty()) {
            ex.getResponseHeaders().add("WWW-Authenticate", "Basic");
            json(ex, 401, Map.of("error", "invalid_client"));
            return;
        }
        Client c = client.get();
        Map<String, String> form = form(ex);
        String grant = form.getOrDefault("grant_type", "");
        if (!c.grants().contains(grant)) {
            json(ex, 400, Map.of("error", "unauthorized_client"));
            return;
        }
        switch (grant) {
            case "authorization_code" -> codeGrant(ex, c, form);
            case "client_credentials" -> {
                Set<String> scopes = scopes(form.get("scope"));
                if (!c.scopes().containsAll(scopes)) {
                    json(ex, 400, Map.of("error", "invalid_scope"));
                    return;
                }
                tokenResponse(ex, sign(c.id(), c.id(), c.audience(), scopes.isEmpty() ? c.scopes() : scopes,
                        accessTokenLifetime, null, null), scopes.isEmpty() ? c.scopes() : scopes);
            }
            case TOKEN_EXCHANGE -> exchange(ex, c, form);
            default -> json(ex, 400, Map.of("error", "unsupported_grant_type"));
        }
    }

    private void codeGrant(HttpExchange ex, Client c, Map<String, String> form) throws IOException {
        String code = form.get("code");
        PendingCode p = code == null ? null : codes.remove(code);              // single use
        if (p == null) {
            if (code != null && usedCodes.containsKey(code)) {
                revokeFromCode(code);                // a code used twice was stolen: revoke what it produced
            }
            json(ex, 400, Map.of("error", "invalid_grant"));
            return;
        }
        usedCodes.put(code, p.client());
        boolean ok = p.client().equals(c.id()) && p.redirectUri().equals(form.get("redirect_uri"))
                && clock.instant().isBefore(p.expires()) && Pkce.matches(form.get("code_verifier"), p.challenge());
        if (!ok) {
            json(ex, 400, Map.of("error", "invalid_grant"));
            return;
        }
        tokenResponse(ex, sign(p.user(), c.id(), c.audience(), p.scopes(), accessTokenLifetime, null, code), p.scopes());
    }

    private void exchange(HttpExchange ex, Client c, Map<String, String> form) throws IOException {
        if (!ACCESS_TOKEN_TYPE.equals(form.get("subject_token_type"))) {
            json(ex, 400, Map.of("error", "invalid_request"));
            return;
        }
        VerifiedJwt subject;
        try {
            subject = verifyOwn(form.get("subject_token"));
        } catch (JwtRejected e) {
            json(ex, 400, Map.of("error", "invalid_grant"));
            return;
        }
        try {
            var grant = exchangePolicy.exchange(c.id(), subject, form.get("audience"), scopes(form.get("scope")),
                    issuer, clock.instant());
            Map<String, Object> claims = new LinkedHashMap<>(grant.claims());
            claims.put("jti", random());
            String token = active.sign(claims);
            issued.put((String) claims.get("jti"), new Issued((String) claims.get("jti"),
                    Instant.ofEpochSecond((Long) claims.get("exp")), null));
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("access_token", token);
            body.put("issued_token_type", ACCESS_TOKEN_TYPE);
            body.put("token_type", "Bearer");
            body.put("expires_in", (Long) claims.get("exp") - clock.instant().getEpochSecond());
            body.put("scope", claims.get("scope"));
            noStore(ex);
            json(ex, 200, body);
        } catch (TokenExchangePolicy.Refused e) {
            json(ex, 400, Map.of("error", e.error(), "error_description", e.getMessage()));
        }
    }

    private void introspect(HttpExchange ex) throws IOException {
        if (authenticate(ex).isEmpty()) {
            json(ex, 401, Map.of("error", "invalid_client"));
            return;
        }
        String token = form(ex).get("token");
        try {
            VerifiedJwt t = verifyOwn(token);
            String jti = (String) t.claims().get("jti");
            if (jti == null || revoked.contains(jti)) {
                json(ex, 200, Map.of("active", false));
                return;
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("active", true);
            body.put("sub", t.subject());
            body.put("scope", String.join(" ", t.scopes()));
            body.put("client_id", t.clientId());
            body.put("aud", t.claims().get("aud"));
            body.put("exp", t.expiresAt().getEpochSecond());
            json(ex, 200, body);
        } catch (JwtRejected e) {
            json(ex, 200, Map.of("active", false));
        }
    }

    private void revoke(HttpExchange ex) throws IOException {
        if (authenticate(ex).isEmpty()) {
            json(ex, 401, Map.of("error", "invalid_client"));
            return;
        }
        try {
            VerifiedJwt t = verifyOwn(form(ex).get("token"));
            Object jti = t.claims().get("jti");
            if (jti != null) {
                revoked.add((String) jti);
            }
        } catch (JwtRejected ignored) {
            // RFC 7009: invalid tokens are not an error
        }
        ex.sendResponseHeaders(200, -1);
        ex.close();
    }

    public boolean isRevoked(String jti) {
        return revoked.contains(jti);
    }

    // ------------------------------------------------------------------ helpers

    private String sign(String subject, String clientId, String audience, Set<String> scopes, Duration lifetime,
                        Map<String, Object> act, String fromCode) {
        Instant now = clock.instant();
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", issuer);
        claims.put("sub", subject);
        claims.put("aud", audience);
        claims.put("client_id", clientId);
        claims.put("scope", String.join(" ", scopes));
        claims.put("iat", now.getEpochSecond());
        claims.put("exp", now.plus(lifetime).getEpochSecond());
        String jti = random();
        claims.put("jti", jti);
        if (act != null) {
            claims.put("act", act);
        }
        issued.put(jti, new Issued(jti, now.plus(lifetime), fromCode));
        return active.sign(claims);
    }

    private VerifiedJwt verifyOwn(String token) {
        String aud = "?";
        try {
            String[] parts = token.split("\\.");
            Object a = Json.object(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)).get("aud");
            aud = a instanceof String s ? s : a instanceof List<?> l && !l.isEmpty() ? String.valueOf(l.getFirst()) : "?";
        } catch (RuntimeException e) {
            throw new JwtRejected(JwtRejected.Reason.MALFORMED);
        }
        Map<String, Jwk> keys = new HashMap<>();
        publicKeys().forEach(k -> keys.put(k.kid(), k));
        return new JwtVerifier(issuer, aud, Set.of("ES256"), kid -> Optional.ofNullable(keys.get(kid)), clock,
                Duration.ofSeconds(30)).verify(token);               // the signature still decides
    }

    private void revokeFromCode(String code) {
        issued.values().stream().filter(i -> code.equals(i.fromCode())).forEach(i -> revoked.add(i.jti()));
    }

    private Optional<Client> authenticate(HttpExchange ex) {
        String h = ex.getRequestHeaders().getFirst("Authorization");
        if (h == null || !h.startsWith("Basic ")) {
            return Optional.empty();
        }
        try {
            String[] pair = new String(Base64.getDecoder().decode(h.substring(6)), StandardCharsets.UTF_8).split(":", 2);
            Client c = clients.get(URLDecoder.decode(pair[0], StandardCharsets.UTF_8));
            if (c == null || pair.length != 2) {
                return Optional.empty();
            }
            boolean ok = MessageDigest.isEqual(c.secretSha256().getBytes(StandardCharsets.UTF_8),
                    sha256(URLDecoder.decode(pair[1], StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8));
            return ok ? Optional.of(c) : Optional.empty();
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static Set<String> scopes(String s) {
        Set<String> out = new LinkedHashSet<>();
        if (s != null) {
            for (String x : s.trim().split("\\s+")) {
                if (!x.isEmpty()) {
                    out.add(x);
                }
            }
        }
        return out;
    }

    private void tokenResponse(HttpExchange ex, String token, Set<String> scopes) throws IOException {
        noStore(ex);
        json(ex, 200, Map.of("access_token", token, "token_type", "Bearer",
                "expires_in", accessTokenLifetime.toSeconds(), "scope", String.join(" ", scopes)));
    }

    private static void noStore(HttpExchange ex) {
        ex.getResponseHeaders().add("Cache-Control", "no-store");
    }

    private static Map<String, String> form(HttpExchange ex) throws IOException {
        Map<String, String> m = new HashMap<>();
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        for (String p : body.split("&")) {
            int eq = p.indexOf('=');
            if (eq > 0) {
                m.put(URLDecoder.decode(p.substring(0, eq), StandardCharsets.UTF_8),
                        URLDecoder.decode(p.substring(eq + 1), StandardCharsets.UTF_8));
            }
        }
        return m;
    }

    private static void json(HttpExchange ex, int status, Object body) throws IOException {
        byte[] b = Json.write(body).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(status, b.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(b);
        }
    }

    private static String random() {
        byte[] b = new byte[24];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }
}
