package dev.foodfleet.ch17.fake;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A payment provider that behaves like real ones on a bad day: some requests get 503, some are
 * slow. It honours Idempotency-Key (a repeated key returns the first authorization) and records
 * what it saw, so tests can check the ambassador's behaviour from the provider's side.
 */
public final class FakePaymentProvider implements AutoCloseable {

    static {
        // The JDK server writes headers and body separately; without TCP_NODELAY, Nagle's algorithm
        // and the client's delayed ACK add ~40 ms to every response on a kept-alive connection.
        System.setProperty("sun.net.httpserver.nodelay", "true");
    }

    private final HttpServer server;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Random random;
    private volatile double unavailable;
    private volatile double slow;
    private volatile long slowMillis;
    private final Map<String, String> authorizationsByKey = new ConcurrentHashMap<>();
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicInteger nextId = new AtomicInteger();
    private volatile String lastAuthorization;

    public FakePaymentProvider(double unavailable, double slow, long slowMillis, long seed) throws IOException {
        this.unavailable = unavailable;
        this.slow = slow;
        this.slowMillis = slowMillis;
        this.random = new Random(seed);
        this.server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setExecutor(executor);
        server.createContext("/v1/authorizations", this::handle);
        server.start();
    }

    public void behave(double unavailable, double slow, long slowMillis) {
        this.unavailable = unavailable;
        this.slow = slow;
        this.slowMillis = slowMillis;
    }

    private void handle(HttpExchange exchange) throws IOException {
        calls.incrementAndGet();
        lastAuthorization = exchange.getRequestHeaders().getFirst("Authorization");
        try (InputStream in = exchange.getRequestBody()) {
            in.readAllBytes();
        }
        double roll;
        synchronized (random) {
            roll = random.nextDouble();
        }
        if (roll < unavailable) {
            send(exchange, 503, "{\"error\":\"temporarily unavailable\"}");
            return;
        }
        if (roll < unavailable + slow) {
            try {
                Thread.sleep(slowMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (exchange.getRequestMethod().equals("POST")) {
            String key = exchange.getRequestHeaders().getFirst("Idempotency-Key");
            String id = key == null ? "auth_" + nextId.incrementAndGet()
                    : authorizationsByKey.computeIfAbsent(key, k -> "auth_" + nextId.incrementAndGet());
            send(exchange, 201, "{\"id\":\"" + id + "\",\"status\":\"authorized\"}");
        } else {
            send(exchange, 200, "{\"status\":\"authorized\"}");
        }
    }

    private static void send(HttpExchange exchange, int status, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public int calls() {
        return calls.get();
    }

    /** Distinct authorizations created: with idempotency keys, retries don't add any. */
    public int authorizationsCreated() {
        return nextId.get();
    }

    public String lastAuthorizationHeader() {
        return lastAuthorization;
    }

    @Override
    public void close() {
        server.stop(0);
        executor.close();
    }
}
