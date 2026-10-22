import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import dev.foodfleet.ch40.cors.CorsPolicy;
import dev.foodfleet.ch40.csrf.FetchMetadataPolicy;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

/**
 * A local lab for what a real browser sends and lets pages read. One HTTPS server answers for four sites, told apart
 * by the Host header (Chromium maps *.test to 127.0.0.1):
 * <ul>
 * <li>foodfleet.test: FoodFleet's web app and its /bff endpoints (cookie session)</li>
 * <li>www.foodfleet.test: a trusted origin on the same site (on the CORS allow-list)</li>
 * <li>reviews.foodfleet.test: the same site, hosting user content (a sibling subdomain an attacker can write to)</li>
 * <li>evil.test: another site</li>
 * </ul>
 * Pages under /attack on any host make one request to foodfleet.test and write the outcome into the page; the
 * server records every request that reaches it (cookies, Origin, Fetch Metadata) and the verdict of each defence.
 * Nothing leaves 127.0.0.1.
 */
public final class BrowserLab {

    static final List<Map<String, Object>> LOG = new ArrayList<>();
    static int port;
    static FetchMetadataPolicy fetchMetadata;
    static CorsPolicy allowList;

    public static void main(String[] args) throws Exception {
        port = Integer.parseInt(args[1]);
        allowList = CorsPolicy.of(Set.of("https://www.foodfleet.test:" + port), Set.of("GET", "POST"),
                Set.of("content-type", "x-xsrf-token"), List.of(), true, Duration.ofMinutes(1));
        fetchMetadata = new FetchMetadataPolicy(allowList);
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (FileInputStream in = new FileInputStream(args[0])) {
            ks.load(in, "changeit".toCharArray());
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(ks, "changeit".toCharArray());
        SSLContext tls = SSLContext.getInstance("TLS");
        tls.init(kmf.getKeyManagers(), null, null);
        HttpsServer server = HttpsServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(tls));
        server.createContext("/", BrowserLab::handle);
        server.start();
        System.out.println("started on " + port);
    }

    static String origin(String host) {
        return "https://" + host + ":" + port;
    }

    static Map<String, String> query(HttpExchange ex) {
        Map<String, String> q = new LinkedHashMap<>();
        String raw = ex.getRequestURI().getRawQuery();
        if (raw != null) {
            for (String kv : raw.split("&")) {
                int i = kv.indexOf('=');
                q.put(URLDecoder.decode(i < 0 ? kv : kv.substring(0, i), StandardCharsets.UTF_8),
                        i < 0 ? "" : URLDecoder.decode(kv.substring(i + 1), StandardCharsets.UTF_8));
            }
        }
        return q;
    }

    static Map<String, String> cookies(HttpExchange ex) {
        Map<String, String> c = new LinkedHashMap<>();
        for (String h : ex.getRequestHeaders().getOrDefault("Cookie", List.of())) {
            for (String kv : h.split(";\\s*")) {
                int i = kv.indexOf('=');
                if (i > 0) {
                    c.put(kv.substring(0, i), kv.substring(i + 1));
                }
            }
        }
        return c;
    }

    static void send(HttpExchange ex, int status, String type, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", type);
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(status, b.length == 0 ? -1 : b.length);
        if (b.length > 0) {
            ex.getResponseBody().write(b);
        }
        ex.close();
    }

    /** CORS response headers for the mode the scenario asks for. */
    static void cors(HttpExchange ex, String mode, boolean preflight) {
        String origin = ex.getRequestHeaders().getFirst("Origin");
        var h = ex.getResponseHeaders();
        h.add("Vary", "Origin");
        if (origin == null) {
            return;
        }
        boolean allow = switch (mode) {
            case "reflect" -> true;
            case "allowlist" -> allowList.allowsOrigin(origin);
            case "allowlist-null" -> allowList.allowsOrigin(origin) || origin.equals("null");
            case "wildcard" -> true;
            default -> false;
        };
        if (!allow) {
            return;
        }
        h.set("Access-Control-Allow-Origin", mode.equals("wildcard") ? "*" : origin);
        h.set("Access-Control-Allow-Credentials", "true");
        if (preflight) {
            h.set("Access-Control-Allow-Methods", "GET, POST");
            h.set("Access-Control-Allow-Headers", "content-type, x-xsrf-token");
        }
    }

    static synchronized void record(Map<String, Object> entry) {
        LOG.add(entry);
    }

    static void handle(HttpExchange ex) throws IOException {
        String host = ex.getRequestHeaders().getFirst("Host").replaceFirst(":\\d+$", "");
        String path = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();
        Map<String, String> q = query(ex);
        Map<String, String> cookies = cookies(ex);

        if (path.equals("/__log")) {
            String json;
            synchronized (BrowserLab.class) {
                json = toJson(LOG);
                LOG.clear();
            }
            send(ex, 200, "application/json", json);
            return;
        }
        if (path.equals("/attack")) {
            send(ex, 200, "text/html; charset=utf-8", attackPage(q.get("kind"), q.getOrDefault("cors", "none"),
                    q.getOrDefault("session", "s_lax")));
            return;
        }
        if (!host.equals("foodfleet.test")) {
            send(ex, 404, "text/plain", "");
            return;
        }
        switch (path) {
            case "/login" -> {
                var h = ex.getResponseHeaders();
                String v = Long.toHexString(System.nanoTime());
                h.add("Set-Cookie", "s_lax=" + v + "; Path=/; Secure; HttpOnly; SameSite=Lax");
                h.add("Set-Cookie", "s_strict=" + v + "; Path=/; Secure; HttpOnly; SameSite=Strict");
                h.add("Set-Cookie", "s_none=" + v + "; Path=/; Secure; HttpOnly; SameSite=None");
                h.add("Set-Cookie", "s_unset=" + v + "; Path=/; Secure; HttpOnly");
                h.add("Set-Cookie", "XSRF-TOKEN=t" + v + "; Path=/; Secure; SameSite=Lax");
                send(ex, 200, "text/html", "<title>logged in</title>");
            }
            case "/app" -> send(ex, 200, "text/html; charset=utf-8", appPage());
            case "/bff/cart/items", "/bff/orders" -> {
                String mode = q.getOrDefault("cors", "none");
                if (method.equals("OPTIONS")) {
                    record(entry(ex, host, path, method, cookies, q, null));
                    cors(ex, mode, true);
                    send(ex, 204, "text/plain", "");
                    return;
                }
                String token = ex.getRequestHeaders().getFirst("X-XSRF-TOKEN");
                boolean tokenOk = token != null && token.equals(cookies.get("XSRF-TOKEN"));
                Map<String, Object> e = entry(ex, host, path, method, cookies, q, tokenOk);
                record(e);
                cors(ex, mode, false);
                String session = q.getOrDefault("session", "s_lax");
                if (path.equals("/bff/orders")) {
                    // the user's order history, if the request carried the session cookie
                    send(ex, 200, "application/json", cookies.containsKey(session)
                            ? "{\"orders\":[{\"id\":\"o-81723\",\"address\":\"No. 12/3, 1st Cross, HSR Layout\",\"phone\":\"+91-98450-xxxxx\"}]}"
                            : "{\"orders\":[]}");
                } else {
                    send(ex, 200, "application/json", "{\"added\":true}");
                }
            }
            default -> send(ex, 404, "text/plain", "");
        }
    }

    static Map<String, Object> entry(HttpExchange ex, String host, String path, String method, Map<String, String> cookies,
            Map<String, String> q, Boolean tokenOk) {
        var h = ex.getRequestHeaders();
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("scenario", q.getOrDefault("scenario", ""));
        e.put("method", method);
        e.put("path", path);
        e.put("cookies", String.join(",", new TreeSet<>(cookies.keySet())));
        e.put("origin", String.valueOf(h.getFirst("Origin")));
        e.put("site", String.valueOf(h.getFirst("Sec-Fetch-Site")));
        e.put("mode", String.valueOf(h.getFirst("Sec-Fetch-Mode")));
        e.put("dest", String.valueOf(h.getFirst("Sec-Fetch-Dest")));
        e.put("contentType", String.valueOf(h.getFirst("Content-Type")));
        if (tokenOk != null) {
            e.put("tokenOk", tokenOk);
            FetchMetadataPolicy.Verdict v = fetchMetadata.check(method, h.getFirst("Sec-Fetch-Site"), h.getFirst("Sec-Fetch-Mode"),
                    h.getFirst("Sec-Fetch-Dest"), h.getFirst("Origin"));
            e.put("fetchMetadataOk", v.allowed());
            e.put("fetchMetadataReason", v.reason());
        }
        return e;
    }

    static String appPage() {
        // FoodFleet's own web app: same origin, copies the XSRF-TOKEN cookie into the header (Spring's csrf.spa())
        return """
                <!doctype html><title>app</title><pre id=out></pre><script>
                const token = document.cookie.split('; ').find(c => c.startsWith('XSRF-TOKEN=')).split('=')[1];
                fetch('/bff/cart/items?scenario=app-own-fetch', {method: 'POST', credentials: 'same-origin',
                  headers: {'Content-Type': 'application/json', 'X-XSRF-TOKEN': token}, body: '{"sku":"veg-biryani"}'})
                  .then(r => r.text()).then(t => out.textContent = 'ok ' + t, e => out.textContent = 'error ' + e)
                  .finally(() => window.done = true);
                </script>""";
    }

    static String attackPage(String kind, String cors, String session) {
        String target = origin("foodfleet.test");
        String s = "scenario=" + kind;
        return switch (kind) {
            case "form" -> """
                    <!doctype html><title>form</title>
                    <form id=f method=POST action="%s/bff/cart/items?%s" enctype="application/x-www-form-urlencoded">
                    <input name=sku value=gift-card-5000></form><script>f.submit()</script>""".formatted(target, s);
            case "link" -> """
                    <!doctype html><title>link</title><a id=a href="%s/bff/orders?%s">orders</a><script>a.click()</script>"""
                    .formatted(target, s);
            case "fetch-simple" -> """
                    <!doctype html><title>fetch-simple</title><pre id=out></pre><script>
                    fetch('%s/bff/cart/items?%s', {method: 'POST', mode: 'no-cors', credentials: 'include',
                      headers: {'Content-Type': 'text/plain'}, body: '{"sku":"gift-card-5000"}'})
                      .then(r => out.textContent = 'sent (opaque response)', e => out.textContent = 'error ' + e)
                      .finally(() => window.done = true);
                    </script>""".formatted(target, s);
            case "fetch-json" -> """
                    <!doctype html><title>fetch-json</title><pre id=out></pre><script>
                    fetch('%s/bff/cart/items?%s&cors=%s', {method: 'POST', credentials: 'include',
                      headers: {'Content-Type': 'application/json'}, body: '{"sku":"gift-card-5000"}'})
                      .then(r => r.text()).then(t => out.textContent = 'read: ' + t, e => out.textContent = 'blocked: ' + e)
                      .finally(() => window.done = true);
                    </script>""".formatted(target, s, cors);
            case "read" -> """
                    <!doctype html><title>read</title><pre id=out></pre><script>
                    fetch('%s/bff/orders?%s&cors=%s&session=%s', {credentials: 'include'})
                      .then(r => r.text()).then(t => out.textContent = 'read: ' + t, e => out.textContent = 'blocked: ' + e)
                      .finally(() => window.done = true);
                    </script>""".formatted(target, s, cors, session);
            case "read-sandboxed" -> """
                    <!doctype html><title>read-sandboxed</title><pre id=out></pre>
                    <iframe sandbox="allow-scripts" srcdoc="<script>fetch('%s/bff/orders?%s&cors=%s&session=%s', {credentials: 'include'})
                      .then(r => r.text()).then(t => parent.postMessage('read: ' + t, '*'), e => parent.postMessage('blocked: ' + e, '*'))</script>"></iframe>
                    <script>addEventListener('message', m => { out.textContent = m.data; window.done = true; });</script>"""
                    .formatted(target, s, cors, session);
            default -> "<!doctype html><title>?</title>";
        };
    }

    static String toJson(List<Map<String, Object>> list) {
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                b.append(',');
            }
            b.append('{');
            int j = 0;
            for (var e : list.get(i).entrySet()) {
                if (j++ > 0) {
                    b.append(',');
                }
                b.append('"').append(e.getKey()).append("\":");
                Object v = e.getValue();
                if (v instanceof Boolean) {
                    b.append(v);
                } else {
                    b.append('"').append(String.valueOf(v).replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
                }
            }
            b.append('}');
        }
        return b.append(']').toString();
    }
}
