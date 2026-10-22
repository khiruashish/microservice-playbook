package dev.foodfleet.ch36.discovery;

import dev.foodfleet.ch36.json.Json;
import dev.foodfleet.ch36.jwt.Jwk;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * The issuer's public keys, found through OpenID Connect discovery and cached. A token with an unknown kid
 * triggers a refresh (the issuer may have rotated its key), but at most once per {@code minRefresh}: otherwise
 * anyone can make every resource server hammer the issuer by sending tokens with made-up kids.
 */
public final class JwksClient {

    private final URI issuer;
    private final Function<URI, String> fetch;
    private final Clock clock;
    private final Duration ttl;
    private final Duration minRefresh;
    private volatile Map<String, Jwk> keys = Map.of();
    private volatile Instant loadedAt = Instant.EPOCH;
    private volatile URI jwksUri;
    private final AtomicLong fetches = new AtomicLong();
    private final Object lock = new Object();

    public JwksClient(URI issuer, Function<URI, String> fetch, Clock clock, Duration ttl, Duration minRefresh) {
        this.issuer = issuer;
        this.fetch = fetch;
        this.clock = clock;
        this.ttl = ttl;
        this.minRefresh = minRefresh;
    }

    public static JwksClient http(URI issuer, Clock clock) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        return new JwksClient(issuer, uri -> get(http, uri), clock, Duration.ofMinutes(10), Duration.ofSeconds(30));
    }

    /** The key for a kid, refreshing the set if it is stale or the kid is new (rate-limited). */
    public Optional<Jwk> key(String kid) {
        Instant now = clock.instant();
        Jwk k = keys.get(kid);
        if (k != null && now.isBefore(loadedAt.plus(ttl))) {
            return Optional.of(k);
        }
        synchronized (lock) {
            k = keys.get(kid);
            boolean stale = !now.isBefore(loadedAt.plus(ttl));
            boolean mayRefresh = !now.isBefore(loadedAt.plus(minRefresh));
            if ((k == null || stale) && mayRefresh) {
                load(now);
                k = keys.get(kid);
            }
            return Optional.ofNullable(k);
        }
    }

    public long fetches() {
        return fetches.get();
    }

    @SuppressWarnings("unchecked")
    private void load(Instant now) {
        try {
            if (jwksUri == null) {
                Map<String, Object> config = Json.object(fetch.apply(issuer.resolve("/.well-known/openid-configuration")));
                fetches.incrementAndGet();
                if (!issuer.toString().equals(config.get("issuer"))) {
                    throw new IllegalStateException("discovery document is for another issuer");
                }
                jwksUri = URI.create((String) config.get("jwks_uri"));
            }
            Map<String, Object> set = Json.object(fetch.apply(jwksUri));
            fetches.incrementAndGet();
            Map<String, Jwk> loaded = new ConcurrentHashMap<>();
            for (Object o : (List<Object>) set.get("keys")) {
                Jwk j = Jwk.fromJson((Map<String, Object>) o);
                loaded.put(j.kid(), j);
            }
            keys = Map.copyOf(loaded);
        } catch (RuntimeException e) {
            // keep serving the keys we have; try again after minRefresh
        }
        loadedAt = now;
    }

    private static String get(HttpClient http, URI uri) {
        try {
            HttpResponse<String> r = http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(3)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() != 200) {
                throw new IllegalStateException(uri + " answered " + r.statusCode());
            }
            return r.body();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
