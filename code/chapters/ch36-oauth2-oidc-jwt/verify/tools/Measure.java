import dev.foodfleet.ch36.authserver.MiniAuthorizationServer;
import dev.foodfleet.ch36.discovery.JwksClient;
import dev.foodfleet.ch36.exchange.TokenExchangeClient;
import dev.foodfleet.ch36.exchange.TokenExchangePolicy;
import dev.foodfleet.ch36.json.Json;
import dev.foodfleet.ch36.jwt.JwtRejected;
import dev.foodfleet.ch36.jwt.JwtSigner;
import dev.foodfleet.ch36.jwt.JwtVerifier;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/**
 * The chapter's measurements, JDK only, over the module's own classes (compiled by run.sh):
 *   crypto        signing and verification speed and token size, ES256 vs RS256
 *   validation    a resource server's cost per request: local JWT verification vs introspection over HTTP
 *   revocation    how long a revoked token keeps working, by token lifetime and validation method (simulated)
 *   rotation      key rotation and made-up kids against a JWKS cache, over HTTP
 *   exchange      token exchange over HTTP: what the downstream token carries, and the cost with a cache
 */
public class Measure {

    public static void main(String[] args) throws Exception {
        // the JDK's HttpServer otherwise leaves Nagle's algorithm on, and small responses wait ~40 ms for
        // delayed ACKs: a loopback artefact that would swamp every HTTP measurement here
        System.setProperty("sun.net.httpserver.nodelay", "true");
        switch (args[0]) {
            case "crypto" -> crypto();
            case "validation" -> validation();
            case "revocation" -> revocation();
            case "rotation" -> rotation();
            case "exchange" -> exchange();
            default -> throw new IllegalArgumentException(args[0]);
        }
    }

    static Map<String, Object> claims(String iss, Instant now) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("iss", iss);
        c.put("sub", "c01");
        c.put("aud", "order-service");
        c.put("client_id", "customer-app");
        c.put("scope", "orders:read orders:write payments:capture");
        c.put("iat", now.getEpochSecond());
        c.put("exp", now.plusSeconds(300).getEpochSecond());
        c.put("jti", "4f1c2d0a9b8e");
        return c;
    }

    // ------------------------------------------------------------------ crypto

    static void crypto() {
        Instant now = Instant.now();
        System.out.println("one thread, 3 s per figure");
        System.out.printf("%-8s%14s%16s%18s%n", "alg", "signs/s", "verifies/s", "token bytes");
        for (String alg : List.of("ES256", "RS256")) {
            JwtSigner signer = JwtSigner.generate("k1", alg);
            JwtVerifier verifier = new JwtVerifier("https://id", "order-service", Set.of(alg),
                    kid -> Optional.of(signer.publicJwk()), Clock.systemUTC(), Duration.ofSeconds(30));
            String token = signer.sign(claims("https://id", now));
            long signs = 0;
            long end = System.nanoTime() + 3_000_000_000L;
            while (System.nanoTime() < end) {
                signer.sign(claims("https://id", now));
                signs++;
            }
            long verifies = 0;
            end = System.nanoTime() + 3_000_000_000L;
            while (System.nanoTime() < end) {
                verifier.verify(token);
                verifies++;
            }
            System.out.printf("%-8s%,14.0f%,16.0f%,18d%n", alg, signs / 3.0, verifies / 3.0, token.length());
        }
    }

    // ------------------------------------------------------------------ validation cost

    static MiniAuthorizationServer server() throws Exception {
        MiniAuthorizationServer as = new MiniAuthorizationServer(Clock.systemUTC(), Duration.ofMinutes(5),
                new TokenExchangePolicy(List.of(new TokenExchangePolicy.ClientRules("order-service",
                        Map.of("payment-service", Set.of("payments:capture")))), Duration.ofMinutes(5)));
        as.register(new MiniAuthorizationServer.Client("order-service", MiniAuthorizationServer.sha256("svc-secret"),
                Set.of(), Set.of("client_credentials", MiniAuthorizationServer.TOKEN_EXCHANGE), Set.of("menu:read"),
                "menu-service"));
        return as.start(0);
    }

    static String form(Map<String, String> f) {
        StringBuilder sb = new StringBuilder();
        f.forEach((k, v) -> sb.append(sb.isEmpty() ? "" : "&").append(URLEncoder.encode(k, StandardCharsets.UTF_8))
                .append('=').append(URLEncoder.encode(v, StandardCharsets.UTF_8)));
        return sb.toString();
    }

    static String basic() {
        return "Basic " + Base64.getEncoder().encodeToString("order-service:svc-secret".getBytes());
    }

    static double[] pct(List<Long> nanos) {
        List<Long> s = new ArrayList<>(nanos);
        s.sort(null);
        return new double[] {s.get(s.size() / 2) / 1e3, s.get((int) (s.size() * 0.99)) / 1e3};
    }

    static void validation() throws Exception {
        try (MiniAuthorizationServer as = server()) {
            String token = as.issue("c01", "customer-app", "order-service", Set.of("orders:read"), Duration.ofMinutes(5));
            JwksClient jwks = JwksClient.http(URI.create(as.issuer()), Clock.systemUTC());
            JwtVerifier verifier = new JwtVerifier(as.issuer(), "order-service", Set.of("ES256"), jwks::key,
                    Clock.systemUTC(), Duration.ofSeconds(30));
            HttpClient http = HttpClient.newHttpClient();
            HttpRequest introspect = HttpRequest.newBuilder(URI.create(as.issuer() + "/introspect"))
                    .header("Authorization", basic()).header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form(Map.of("token", token)))).build();
            for (int i = 0; i < 500; i++) {                                // warm-up
                verifier.verify(token);
                http.send(introspect, HttpResponse.BodyHandlers.ofString());
            }
            int n = 3000;
            List<Long> local = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                long t = System.nanoTime();
                verifier.verify(token);
                local.add(System.nanoTime() - t);
            }
            List<Long> remote = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                long t = System.nanoTime();
                Map<String, Object> r = Json.object(http.send(introspect, HttpResponse.BodyHandlers.ofString()).body());
                if (!Boolean.TRUE.equals(r.get("active"))) {
                    throw new IllegalStateException("inactive");
                }
                remote.add(System.nanoTime() - t);
            }
            double[] l = pct(local);
            double[] r = pct(remote);
            System.out.println("per request, " + n + " requests, the authorization server on the same machine (loopback)");
            System.out.printf("%-44s%12s%12s%24s%n", "method", "p50 us", "p99 us", "calls to the issuer");
            System.out.printf("%-44s%12.0f%12.0f%24s%n", "JWT verified locally (ES256, cached JWKS)", l[0], l[1],
                    jwks.fetches() + " (discovery + JWKS, once)");
            System.out.printf("%-44s%12.0f%12.0f%24s%n", "opaque token, introspected per request", r[0], r[1],
                    "one per request");
        }
    }

    // ------------------------------------------------------------------ revocation window

    static void revocation() {
        Random rng = new Random(36);
        int sessions = 100_000;
        System.out.println("100,000 tokens revoked (logout, compromised account) at a random moment of their life;");
        System.out.println("how long each kept working afterwards");
        System.out.printf("%-14s%-40s%14s%14s%n", "lifetime", "resource servers check by", "mean", "worst");
        for (int minutes : List.of(5, 15, 60)) {
            double life = minutes * 60.0;
            for (String method : List.of("local JWT verification", "introspection, cached 30 s", "introspection, no cache")) {
                double sum = 0;
                double worst = 0;
                for (int i = 0; i < sessions; i++) {
                    double revokedAt = rng.nextDouble() * life;
                    double exposure = switch (method) {
                        case "local JWT verification" -> life - revokedAt;
                        case "introspection, cached 30 s" -> Math.min(life - revokedAt, rng.nextDouble() * 30);
                        default -> 0;
                    };
                    sum += exposure;
                    worst = Math.max(worst, exposure);
                }
                System.out.printf("%-14s%-40s%12.0f s%12.0f s%n", minutes + " min", method, sum / sessions, worst);
            }
        }
    }

    // ------------------------------------------------------------------ key rotation

    static final class SimClock extends Clock {
        Instant now = Instant.now();

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public java.time.ZoneId getZone() {
            return java.time.ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId z) {
            return this;
        }
    }

    /** One request a second for ten simulated minutes, a key rotation at the start; counts rejected requests. */
    static int rejectedDuringRotation(MiniAuthorizationServer as, boolean staged) {
        SimClock clock = new SimClock();
        JwksClient jwks = new JwksClient(URI.create(as.issuer()), Measure::get, clock, Duration.ofMinutes(5),
                Duration.ofSeconds(30));
        JwtVerifier verifier = new JwtVerifier(as.issuer(), "order-service", Set.of("ES256"), jwks::key, clock,
                Duration.ofDays(1));                                   // simulated time: don't let tokens expire
        verifier.verify(as.issue("c01", "app", "order-service", Set.of("orders:read"), Duration.ofHours(1)));
        int rejected = 0;
        if (staged) {
            as.publishNextKey();                                     // step 1: publish, keep signing with the old key
            for (int s = 0; s < 360; s++) {                          // wait longer than the verifiers' cache TTL
                clock.now = clock.now.plusSeconds(1);
                rejected += tryVerify(verifier, as.issue("c01", "app", "order-service", Set.of("orders:read"),
                        Duration.ofHours(1))) ? 0 : 1;
            }
            as.activateNewestKey();                                  // step 2: sign with the new key
        } else {
            as.rotateKey();
        }
        for (int s = 0; s < 600; s++) {
            clock.now = clock.now.plusSeconds(1);
            rejected += tryVerify(verifier, as.issue("c01", "app", "order-service", Set.of("orders:read"),
                    Duration.ofHours(1))) ? 0 : 1;
        }
        return rejected;
    }

    static void rotation() throws Exception {
        try (MiniAuthorizationServer as = server()) {
            System.out.println("a resource server's JWKS cache: refreshed every 5 min, and on an unknown kid at most every 30 s");
            System.out.println();
            for (int minRefreshSeconds : List.of(30, 0)) {
                JwksClient jwks = new JwksClient(URI.create(as.issuer()), Measure::get, Clock.systemUTC(),
                        Duration.ofMinutes(10), Duration.ofSeconds(minRefreshSeconds));
                JwtVerifier verifier = new JwtVerifier(as.issuer(), "order-service", Set.of("ES256"), jwks::key,
                        Clock.systemUTC(), Duration.ofSeconds(30));
                verifier.verify(as.issue("c01", "app", "order-service", Set.of("orders:read"), Duration.ofMinutes(5)));
                long before = jwks.fetches();
                int rejected = 0;
                JwtSigner stranger = JwtSigner.generate("x", "ES256");
                String[] p = stranger.sign(claims(as.issuer(), Instant.now())).split("\\.");
                for (int i = 0; i < 1000; i++) {
                    String header = Base64.getUrlEncoder().withoutPadding().encodeToString(
                            ("{\"alg\":\"ES256\",\"typ\":\"at+jwt\",\"kid\":\"unknown-" + i + "\"}").getBytes());
                    rejected += tryVerify(verifier, header + "." + p[1] + "." + p[2]) ? 0 : 1;
                }
                System.out.printf("1,000 tokens with unknown kids, refresh limit %2d s: rejected %,d, JWKS fetched %,d times%n",
                        minRefreshSeconds, rejected, jwks.fetches() - before);
            }
            System.out.println();
            System.out.printf("key rotation, one request a second for 10 minutes after the switch:%n");
            System.out.printf("  sudden (new key signs at once)                      %4d requests rejected%n",
                    rejectedDuringRotation(as, false));
            System.out.printf("  staged (published 6 minutes before it signs)        %4d requests rejected%n",
                    rejectedDuringRotation(as, true));
        }
    }

    static boolean tryVerify(JwtVerifier v, String t) {
        try {
            v.verify(t);
            return true;
        } catch (JwtRejected e) {
            return false;
        }
    }

    static String get(URI uri) {
        try {
            return HttpClient.newHttpClient().send(HttpRequest.newBuilder(uri).GET().build(),
                    HttpResponse.BodyHandlers.ofString()).body();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------------ token exchange

    @SuppressWarnings("unchecked")
    static void exchange() throws Exception {
        try (MiniAuthorizationServer as = server()) {
            String user = as.issue("c01", "customer-app", "order-service",
                    Set.of("orders:read", "orders:write", "payments:capture", "profile:read"), Duration.ofMinutes(15));
            TokenExchangeClient client = new TokenExchangeClient(URI.create(as.issuer() + "/token"), "order-service",
                    "svc-secret", Clock.systemUTC());
            String downstream = client.tokenFor(user, "payment-service", "payments:capture");
            Map<String, Object> u = payload(user);
            Map<String, Object> d = payload(downstream);
            System.out.printf("%-12s%-18s%-56s%-26s%10s%n", "token", "aud", "scope", "act", "lifetime");
            System.out.printf("%-12s%-18s%-56s%-26s%8d s%n", "user's", u.get("aud"), u.get("scope"), "-",
                    (Long) u.get("exp") - (Long) u.get("iat"));
            System.out.printf("%-12s%-18s%-56s%-26s%8d s%n", "exchanged", d.get("aud"), d.get("scope"),
                    Json.write(d.get("act")), (Long) d.get("exp") - (Long) d.get("iat"));
            int n = 2000;
            long t0 = System.nanoTime();
            for (int i = 0; i < n; i++) {
                client.tokenFor(user, "payment-service", "payments:capture");
            }
            double cached = (System.nanoTime() - t0) / 1e3 / n;
            List<Long> fresh = new ArrayList<>();
            for (int i = 0; i < 300; i++) {
                String other = as.issue("c" + i, "customer-app", "order-service", Set.of("payments:capture"),
                        Duration.ofMinutes(15));
                long t = System.nanoTime();
                client.tokenFor(other, "payment-service", "payments:capture");
                fresh.add(System.nanoTime() - t);
            }
            double[] f = pct(fresh);
            System.out.printf("%nexchange cost: first call per user p50 %.0f us, p99 %.0f us (an HTTP call to the issuer); "
                    + "later calls from the cache %.1f us%n", f[0], f[1], cached);
        }
    }

    static Map<String, Object> payload(String jwt) {
        return Json.object(new String(Base64.getUrlDecoder().decode(jwt.split("\\.")[1]), StandardCharsets.UTF_8));
    }
}
