package dev.foodfleet.ch71.deprecation;

import com.sun.net.httpserver.HttpServer;
import dev.foodfleet.ch71.json.Json;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Deprecation that consumers can see and providers can measure. The provider marks operations {@code deprecated} in
 * its OpenAPI document with {@code x-deprecated-since} and {@code x-sunset}; its gateway turns them into response
 * headers (RFC 9745's {@code Deprecation} as a structured-field date, RFC 8594's {@code Sunset} as an HTTP-date, and a
 * {@code Link} to the migration guide, as Spring Framework 7's {@code StandardApiVersionDeprecationHandler} does for
 * versions) and logs who called what. FoodFleet's client library warns once per deprecated operation.
 */
public final class Deprecations {

    /** One deprecated operation: method, path template (as a regex), and its dates. */
    public record Deprecated(String method, String pathRegex, Instant since, Instant sunset, String link) {
    }

    /** One line of the gateway's access log. */
    public record Access(String client, String method, String path, boolean deprecated) {
    }

    /** RFC 9110's IMF-fixdate. Java's RFC_1123_DATE_TIME writes "6 Jul", which an HTTP-date doesn't allow. */
    static final DateTimeFormatter HTTP_DATE = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.ENGLISH).withZone(ZoneOffset.UTC);

    private Deprecations() {
    }

    public static List<Deprecated> fromSpec(Map<String, Object> api) {
        List<Deprecated> out = new ArrayList<>();
        for (Map.Entry<String, Object> p : Json.object(api.get("paths")).entrySet()) {
            for (Map.Entry<String, Object> m : Json.object(p.getValue()).entrySet()) {
                if (!(m.getValue() instanceof Map<?, ?>)) {
                    continue;
                }
                Map<String, Object> op = Json.object(m.getValue());
                if (Boolean.TRUE.equals(op.get("deprecated")) && op.get("x-sunset") != null) {
                    out.add(new Deprecated(m.getKey().toUpperCase(), p.getKey().replaceAll("\\{[^}]+}", "[^/]+"), Instant.parse(String.valueOf(op.get("x-deprecated-since"))),
                            Instant.parse(String.valueOf(op.get("x-sunset"))), String.valueOf(op.get("x-migration-guide"))));
                }
            }
        }
        return out;
    }

    /** The response headers for a deprecated operation. */
    public static Map<String, String> headers(Deprecated d) {
        Map<String, String> h = new TreeMap<>();
        h.put("Deprecation", "@" + d.since().getEpochSecond()); // RFC 9745: an sf-date
        h.put("Sunset", HTTP_DATE.format(d.sunset())); // RFC 8594: an HTTP-date (IMF-fixdate: two-digit day, GMT)
        h.put("Link", "<" + d.link() + ">; rel=\"deprecation\"; type=\"text/html\"");
        return h;
    }

    /** FoodFleet's client library: reads the headers, warns once per operation, and remembers the sunset. */
    public static final class Client {

        private final HttpClient http = HttpClient.newHttpClient();
        private final String name;
        public final Set<String> warned = new TreeSet<>();

        public Client(String name) {
            this.name = name;
        }

        public HttpResponse<String> get(String url) throws IOException, InterruptedException {
            HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(url)).header("X-Client-Id", name).build(), HttpResponse.BodyHandlers.ofString());
            r.headers().firstValue("Deprecation").ifPresent(d -> {
                Instant since = Instant.ofEpochSecond(Long.parseLong(d.substring(1)));
                Instant sunset = r.headers().firstValue("Sunset").map(s -> ZonedDateTime.parse(s, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).orElse(null);
                if (warned.add(URI.create(url).getPath())) {
                    System.out.printf("    [%s] WARN %s is deprecated since %s, removed %s; see %s%n", name, URI.create(url).getPath(), since.toString().substring(0, 10),
                            sunset == null ? "(no date)" : sunset.toString().substring(0, 10), r.headers().firstValue("Link").orElse("-"));
                }
            });
            return r;
        }
    }

    /**
     * Eight consumer applications call payment-service for a while; some use the client library, some plain HTTP.
     * The access log and the catalog then disagree about who depends on the deprecated operation.
     */
    public static void experiment(Path spec, Path catalogDir) throws Exception {
        Map<String, Object> api = Json.object(Json.parse(Files.readString(spec)));
        // the governance fix for the planted problem: a deprecated operation gets its dates
        Map<String, Object> op = Json.object(Json.object(Json.object(api.get("paths")).get("/payment-methods")).get("get"));
        op.put("x-deprecated-since", "2026-01-05T00:00:00Z");
        op.put("x-sunset", "2026-07-06T00:00:00Z");
        op.put("x-migration-guide", "https://developer.foodfleet.dev/apis/payment-service/migrations/payment-methods");
        List<Deprecated> deprecated = fromSpec(api);
        List<Access> log = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 128);
        server.createContext("/", ex -> {
            try (ex) {
                String path = ex.getRequestURI().getPath();
                Deprecated d = deprecated.stream().filter(x -> x.method().equals(ex.getRequestMethod()) && path.matches(x.pathRegex())).findFirst().orElse(null);
                if (d != null) {
                    headers(d).forEach((k, v) -> ex.getResponseHeaders().add(k, v));
                }
                log.add(new Access(String.valueOf(ex.getRequestHeaders().getFirst("X-Client-Id")), ex.getRequestMethod(), path, d != null));
                byte[] b = "{}".getBytes();
                ex.sendResponseHeaders(200, b.length);
                ex.getResponseBody().write(b);
            }
        });
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        try {
            System.out.println("payment-service's GET /payment-methods: deprecated, x-deprecated-since 2026-01-05, x-sunset 2026-07-06; the gateway's headers:");
            headers(deprecated.get(0)).forEach((k, v) -> System.out.println("  " + k + ": " + v));
            // who calls what: {app, uses the client library?, calls the deprecated operation?, calls per "day"}
            Object[][] apps = {{"checkout-bff", true, true, 40}, {"order-service", true, false, 60}, {"refund-service", true, false, 10}, {"ledger-service", true, false, 25},
                    {"restaurant-portal", true, true, 5}, {"support-console", false, true, 3}, {"settlement-job", false, false, 2}, {"analytics-ingest", false, true, 1}};
            AtomicInteger calls = new AtomicInteger();
            Map<String, Client> clients = new TreeMap<>();
            System.out.println("eight applications call for 7 'days'; the client library's warnings:");
            for (int day = 0; day < 7; day++) {
                for (Object[] a : apps) {
                    Client c = clients.computeIfAbsent((String) a[0], Client::new);
                    for (int i = 0; i < (int) a[3]; i++) {
                        String path = (boolean) a[2] && i % 2 == 0 ? "/payment-methods" : "/payments/pay-" + i;
                        if ((boolean) a[1]) {
                            c.get(base + path);
                        } else { // plain HTTP: the same request, but nobody reads the headers
                            HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(base + path)).header("X-Client-Id", (String) a[0]).build(),
                                    HttpResponse.BodyHandlers.discarding());
                        }
                        calls.incrementAndGet();
                    }
                }
            }
            Map<String, Integer> deprecatedCalls = new TreeMap<>();
            log.stream().filter(Access::deprecated).forEach(a -> deprecatedCalls.merge(a.client(), 1, Integer::sum));
            Set<String> declared = consumersInCatalog(catalogDir, "payment-service");
            System.out.printf("%d calls logged; callers of the deprecated operation:%n", log.size());
            System.out.printf("  %-20s %8s %26s %22s%n", "application", "calls", "declared in the catalog", "warned by the library");
            deprecatedCalls.forEach((app, n) -> System.out.printf("  %-20s %8d %26s %22s%n", app, n, declared.contains(app) ? "yes" : "NO", clients.get(app).warned.isEmpty() ? "no" : "yes"));
            Set<String> callers = new TreeSet<>();
            log.forEach(a -> callers.add(a.client()));
            Set<String> undeclared = new TreeSet<>(callers);
            undeclared.removeAll(declared);
            Set<String> silent = new TreeSet<>(declared);
            silent.removeAll(callers);
            System.out.printf("the catalog declares %d consumers of payment-service; the log saw %d callers; called without being declared: %s; declared but never called: %s%n",
                    declared.size(), callers.size(), undeclared, silent);
        } finally {
            server.stop(0);
        }
    }

    static Set<String> consumersInCatalog(Path dir, String api) throws IOException {
        Set<String> out = new TreeSet<>();
        for (Path f : Files.list(dir).sorted().toList()) {
            Map<String, Object> e = Json.object(Json.parse(Files.readString(f)));
            if ("Component".equals(e.get("kind"))) {
                Object consumes = Json.object(e.get("spec")).get("consumesApis");
                if (consumes instanceof List<?> l && l.contains(api)) {
                    out.add(String.valueOf(Json.object(e.get("metadata")).get("name")));
                }
            }
        }
        return out;
    }

}
