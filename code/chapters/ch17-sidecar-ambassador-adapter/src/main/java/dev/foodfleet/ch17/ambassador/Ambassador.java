package dev.foodfleet.ch17.ambassador;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * An ambassador: a local proxy, in the same pod, that represents a remote service to the app.
 * kitchen-service calls {@code http://localhost:15001/v1/...}; the ambassador adds the provider's
 * credentials, applies timeouts, retries what is safe to retry, opens a circuit when the provider
 * is down, and counts everything. The app's code knows none of this, in any language.
 *
 * <p>Plain JDK (HttpServer, HttpClient, virtual threads) so it runs anywhere; a production
 * ambassador is usually Envoy or a mesh sidecar configured the same way.
 */
public final class Ambassador implements AutoCloseable {

    static {
        // The JDK server writes headers and body separately; without TCP_NODELAY, Nagle's algorithm
        // and the client's delayed ACK add ~40 ms to every response on a kept-alive connection.
        System.setProperty("sun.net.httpserver.nodelay", "true");
    }

    /** Headers that belong to one hop, or that the JDK client sets itself. */
    private static final Set<String> NOT_FORWARDED = Set.of("connection", "keep-alive", "proxy-connection",
            "transfer-encoding", "te", "trailer", "upgrade", "host", "content-length", "expect", "authorization");
    private static final Set<Integer> RETRYABLE_STATUS = Set.of(502, 503, 504);

    private final AmbassadorConfig config;
    private final HttpServer server;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final HttpClient client;
    private final CircuitBreaker breaker;
    private final Metrics metrics = new Metrics();
    private volatile String token = "";
    private volatile FileTime tokenModified = FileTime.fromMillis(0);

    public record Metrics(AtomicLong requests, AtomicLong upstreamCalls, AtomicLong retries,
                          AtomicLong failures, AtomicLong shortCircuited) {
        Metrics() {
            this(new AtomicLong(), new AtomicLong(), new AtomicLong(), new AtomicLong(), new AtomicLong());
        }

        public String prometheus() {
            return "ambassador_requests_total " + requests + "\n"
                    + "ambassador_upstream_calls_total " + upstreamCalls + "\n"
                    + "ambassador_retries_total " + retries + "\n"
                    + "ambassador_failures_total " + failures + "\n"
                    + "ambassador_short_circuited_total " + shortCircuited + "\n";
        }
    }

    public Ambassador(AmbassadorConfig config) throws IOException {
        this.config = config;
        this.breaker = new CircuitBreaker(config.breakerThreshold(), config.breakerOpenFor(), System::nanoTime);
        this.client = HttpClient.newBuilder().executor(executor).version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(config.perTryTimeout()).build();
        // Loopback only: nothing outside the pod can use the ambassador or its credentials.
        this.server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), config.listenPort()), 0);
        server.setExecutor(executor);
        server.createContext("/__ambassador/health", ex -> respond(ex, 200, Map.of(), "ok\n".getBytes()));
        server.createContext("/__ambassador/metrics",
                ex -> respond(ex, 200, Map.of(), metrics.prometheus().getBytes(StandardCharsets.UTF_8)));
        server.createContext("/", this::proxy);
        server.start();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    public Metrics metrics() {
        return metrics;
    }

    public CircuitBreaker breaker() {
        return breaker;
    }

    private void proxy(HttpExchange exchange) throws IOException {
        metrics.requests().incrementAndGet();
        byte[] body;
        try (InputStream in = exchange.getRequestBody()) {
            body = in.readAllBytes();
        }
        String method = exchange.getRequestMethod().toUpperCase(Locale.ROOT);
        boolean retrySafe = method.equals("GET") || method.equals("HEAD")
                || exchange.getRequestHeaders().containsKey("Idempotency-key");
        int attempts = retrySafe ? config.maxAttempts() : 1;
        URI target = config.upstream().resolve(exchange.getRequestURI().toString());

        String lastError = "no attempt made";
        for (int attempt = 1; attempt <= attempts; attempt++) {
            if (!breaker.allow()) {
                metrics.shortCircuited().incrementAndGet();
                respond(exchange, 503, Map.of("X-Ambassador", List.of("circuit-open")), new byte[0]);
                return;
            }
            if (attempt > 1) {
                metrics.retries().incrementAndGet();
                sleep(backoff(attempt));
            }
            metrics.upstreamCalls().incrementAndGet();
            try {
                HttpResponse<byte[]> response = client.send(upstreamRequest(exchange, method, target, body),
                        HttpResponse.BodyHandlers.ofByteArray());
                if (RETRYABLE_STATUS.contains(response.statusCode())) {
                    breaker.failure();
                    lastError = "upstream " + response.statusCode();
                    if (attempt < attempts) {
                        continue;
                    }
                } else {
                    breaker.success();
                }
                Map<String, List<String>> headers = new java.util.HashMap<>(response.headers().map());
                headers.keySet().removeIf(h -> NOT_FORWARDED.contains(h.toLowerCase(Locale.ROOT)));
                headers.put("X-Ambassador-Attempts", List.of(Integer.toString(attempt)));
                if (response.statusCode() >= 500) {
                    metrics.failures().incrementAndGet();
                }
                respond(exchange, response.statusCode(), headers, response.body());
                return;
            } catch (IOException e) {                     // includes HttpTimeoutException
                breaker.failure();
                lastError = e.getClass().getSimpleName();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        metrics.failures().incrementAndGet();
        respond(exchange, 504, Map.of("X-Ambassador", List.of(lastError),
                "X-Ambassador-Attempts", List.of(Integer.toString(attempts))), new byte[0]);
    }

    private HttpRequest upstreamRequest(HttpExchange exchange, String method, URI target, byte[] body)
            throws IOException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(target)
                .timeout(config.perTryTimeout())
                .method(method, body.length == 0 ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofByteArray(body));
        exchange.getRequestHeaders().forEach((name, values) -> {
            if (!NOT_FORWARDED.contains(name.toLowerCase(Locale.ROOT))) {
                values.forEach(v -> builder.header(name, v));
            }
        });
        return builder.header("Authorization", "Bearer " + currentToken()).build();
    }

    /** Re-read the mounted secret when Kubernetes rotates it; the app never sees the token. */
    private String currentToken() throws IOException {
        FileTime modified = Files.getLastModifiedTime(config.tokenFile());
        if (!modified.equals(tokenModified)) {
            token = Files.readString(config.tokenFile()).strip();
            tokenModified = modified;
        }
        return token;
    }

    private Duration backoff(int attempt) {
        long base = config.backoff().toMillis() << (attempt - 2);          // 1x, 2x, 4x ...
        return Duration.ofMillis(base / 2 + ThreadLocalRandom.current().nextLong(base / 2 + 1));   // jitter
    }

    private static void sleep(Duration d) {
        try {
            Thread.sleep(d);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void respond(HttpExchange exchange, int status, Map<String, List<String>> headers, byte[] body)
            throws IOException {
        headers.forEach((name, values) -> exchange.getResponseHeaders().put(name, values));
        exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    @Override
    public void close() {
        server.stop(0);
        client.close();
        executor.close();
    }

    public static void main(String[] args) throws IOException {
        AmbassadorConfig config = new AmbassadorConfig(
                Integer.parseInt(env("LISTEN_PORT", "15001")),
                URI.create(env("UPSTREAM", "https://api.payments.example")),
                Duration.ofMillis(Long.parseLong(env("PER_TRY_TIMEOUT_MS", "400"))),
                Integer.parseInt(env("MAX_ATTEMPTS", "3")),
                Duration.ofMillis(Long.parseLong(env("BACKOFF_MS", "25"))),
                java.nio.file.Path.of(env("TOKEN_FILE", "/var/run/secrets/payment/token")),
                Integer.parseInt(env("BREAKER_THRESHOLD", "20")),
                Duration.ofSeconds(Long.parseLong(env("BREAKER_OPEN_SECONDS", "10"))));
        new Ambassador(config);
        System.out.println("ambassador listening on 127.0.0.1:" + config.listenPort() + " -> " + config.upstream());
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
