package dev.foodfleet.ch36.exchange;

import dev.foodfleet.ch36.json.Json;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * The calling service's side of token exchange: trade the user's token (issued to us) for one meant for the next
 * service, and cache it until shortly before it expires. The cache key is a hash of the user's token, never the
 * token itself, so a heap dump of the cache doesn't hold usable credentials.
 */
public final class TokenExchangeClient {

    private record Cached(String token, Instant refreshAfter) {
    }

    private final URI tokenEndpoint;
    private final String basicAuth;
    private final Clock clock;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    public TokenExchangeClient(URI tokenEndpoint, String clientId, String clientSecret, Clock clock) {
        this.tokenEndpoint = tokenEndpoint;
        this.basicAuth = "Basic " + Base64.getEncoder().encodeToString(
                (enc(clientId) + ":" + enc(clientSecret)).getBytes(StandardCharsets.UTF_8));
        this.clock = clock;
    }

    public String tokenFor(String userToken, String audience, String scope) {
        String key = sha256(userToken) + "|" + audience + "|" + scope;
        Cached c = cache.get(key);
        if (c != null && clock.instant().isBefore(c.refreshAfter())) {
            return c.token();
        }
        Map<String, String> form = Map.of(
                "grant_type", "urn:ietf:params:oauth:grant-type:token-exchange",
                "subject_token", userToken,
                "subject_token_type", "urn:ietf:params:oauth:token-type:access_token",
                "audience", audience,
                "scope", scope);
        String body = form.entrySet().stream().map(e -> enc(e.getKey()) + "=" + enc(e.getValue()))
                .collect(Collectors.joining("&"));
        try {
            HttpResponse<String> r = http.send(HttpRequest.newBuilder(tokenEndpoint).timeout(Duration.ofSeconds(3))
                    .header("Authorization", basicAuth).header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
            Map<String, Object> json = Json.object(r.body());
            if (r.statusCode() != 200) {
                throw new IllegalStateException("token exchange refused: " + json.get("error"));
            }
            String token = (String) json.get("access_token");
            long expiresIn = ((Number) json.get("expires_in")).longValue();
            cache.put(key, new Cached(token, clock.instant().plusSeconds(Math.max(0, expiresIn - 30))));
            return token;
        } catch (IOException e) {
            throw new IllegalStateException("authorization server unreachable", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
