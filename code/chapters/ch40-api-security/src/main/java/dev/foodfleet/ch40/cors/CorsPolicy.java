package dev.foodfleet.ch40.cors;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Which browser origins may call the API with the user's cookies and read the answer. An allow-list of exact
 * origins (scheme, host, port), compared after normalising both sides: no suffix matching, no regular
 * expressions, no "null", no wildcard together with credentials.
 * <p>
 * CORS is a relaxation of the same-origin policy, enforced by the browser on behalf of the user. It does not stop
 * a request from being sent (a form post or a "simple" fetch goes out without asking), and it means nothing to
 * curl. It decides whether a page on another origin may read the response, and whether a non-simple request is
 * sent at all after a preflight.
 */
public final class CorsPolicy {

    private final Set<String> origins;
    private final Set<String> methods;
    private final Set<String> headers;
    private final List<String> exposed;
    private final boolean credentials;
    private final Duration maxAge;

    private CorsPolicy(Set<String> origins, Set<String> methods, Set<String> headers, List<String> exposed,
            boolean credentials, Duration maxAge) {
        this.origins = origins;
        this.methods = methods;
        this.headers = headers;
        this.exposed = exposed;
        this.credentials = credentials;
        this.maxAge = maxAge;
    }

    /**
     * @param origins each "https://host" or "https://host:port"; "http" only for localhost (development)
     * @throws IllegalArgumentException for "*", "null", patterns, paths, or plain http to a real host
     */
    public static CorsPolicy of(Set<String> origins, Set<String> methods, Set<String> headers, List<String> exposed,
            boolean credentials, Duration maxAge) {
        Set<String> normalised = new TreeSet<>();
        for (String o : origins) {
            String n = normalise(o).orElseThrow(() -> new IllegalArgumentException("not an origin: " + o));
            if (n.startsWith("http://") && !isLoopback(n)) {
                throw new IllegalArgumentException("plain http origin outside development: " + o);
            }
            normalised.add(n);
        }
        Set<String> m = methods.stream().map(s -> s.toUpperCase(Locale.ROOT)).collect(Collectors.toCollection(TreeSet::new));
        Set<String> h = headers.stream().map(s -> s.toLowerCase(Locale.ROOT)).collect(Collectors.toCollection(TreeSet::new));
        return new CorsPolicy(Set.copyOf(normalised), Set.copyOf(m), Set.copyOf(h), List.copyOf(exposed), credentials, maxAge);
    }

    /**
     * The serialised origin as browsers send it: lower-case scheme and host, default port omitted, nothing else.
     * Anything that is not exactly that (a path, user info, a wildcard, "null") is not an origin.
     */
    public static Optional<String> normalise(String origin) {
        if (origin == null || origin.isEmpty() || origin.length() > 2048 || origin.indexOf('*') >= 0
                || origin.chars().anyMatch(c -> c <= ' ' || c >= 0x7f)) {
            return Optional.empty();
        }
        URI u;
        try {
            u = new URI(origin);
        } catch (Exception e) {
            return Optional.empty();
        }
        String scheme = u.getScheme() == null ? null : u.getScheme().toLowerCase(Locale.ROOT);
        if (!"https".equals(scheme) && !"http".equals(scheme)) {
            return Optional.empty();
        }
        if (u.getRawUserInfo() != null || u.getHost() == null || u.getRawQuery() != null || u.getRawFragment() != null
                || (u.getRawPath() != null && !u.getRawPath().isEmpty())) {
            return Optional.empty();
        }
        String host = u.getHost().toLowerCase(Locale.ROOT);
        int port = u.getPort();
        boolean defaultPort = port == -1 || ("https".equals(scheme) && port == 443) || ("http".equals(scheme) && port == 80);
        return Optional.of(scheme + "://" + host + (defaultPort ? "" : ":" + port));
    }

    public boolean allowsOrigin(String originHeader) {
        return normalise(originHeader).map(origins::contains).orElse(false);
    }

    /**
     * Headers for an actual (non-preflight) response. Always {@code Vary: Origin}, so a shared cache never hands
     * one origin's CORS answer to another.
     */
    public Map<String, String> actual(String originHeader) {
        Map<String, String> h = new LinkedHashMap<>();
        h.put("Vary", "Origin");
        if (allowsOrigin(originHeader)) {
            h.put("Access-Control-Allow-Origin", originHeader);
            if (credentials) {
                h.put("Access-Control-Allow-Credentials", "true");
            }
            if (!exposed.isEmpty()) {
                h.put("Access-Control-Expose-Headers", String.join(", ", exposed));
            }
        }
        return h;
    }

    /**
     * Headers for a preflight ({@code OPTIONS} with {@code Access-Control-Request-Method}), or empty when the
     * preflight is refused, in which case the browser does not send the actual request.
     */
    public Optional<Map<String, String>> preflight(String originHeader, String requestMethod, String requestHeaders) {
        if (!allowsOrigin(originHeader) || requestMethod == null
                || !methods.contains(requestMethod.toUpperCase(Locale.ROOT))) {
            return Optional.empty();
        }
        if (requestHeaders != null && !requestHeaders.isBlank()) {
            for (String hdr : requestHeaders.split(",")) {
                if (!headers.contains(hdr.strip().toLowerCase(Locale.ROOT))) {
                    return Optional.empty();
                }
            }
        }
        Map<String, String> h = new LinkedHashMap<>(actual(originHeader));
        h.put("Vary", "Origin, Access-Control-Request-Method, Access-Control-Request-Headers");
        h.put("Access-Control-Allow-Methods", String.join(", ", methods));
        if (!headers.isEmpty()) {
            h.put("Access-Control-Allow-Headers", String.join(", ", headers));
        }
        h.put("Access-Control-Max-Age", Long.toString(maxAge.toSeconds()));
        return Optional.of(h);
    }

    public Set<String> origins() {
        return origins;
    }

    private static boolean isLoopback(String origin) {
        return origin.startsWith("http://localhost") || origin.startsWith("http://127.0.0.1");
    }
}
