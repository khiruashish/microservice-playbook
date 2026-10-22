package dev.foodfleet.ch77.provider;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.foodfleet.ch77.Json;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A stand-in for a hosted LLM API, with the behaviours a gateway has to handle, as parameters rather than as a model:
 * streamed tokens (server-sent events) after a time to first token, an in-flight limit answered with 429 and
 * retry-after, occasional 529 overloads, and occasional streams that stall after the first token. Two models: "large"
 * (slower, first token after about 700 ms, 50 tokens/s) and "small" (about 300 ms, 150 tokens/s). It generates no text.
 */
public final class StubModel implements AutoCloseable {

    public record Behaviour(int inFlightLimit, double overloadRate, double stallRate, double stallSeconds) {
        public static final Behaviour DEFAULT = new Behaviour(8, 0.02, 0.03, 60);
    }

    private final HttpServer server;
    private final Behaviour behaviour;
    private final Random random;
    private final AtomicInteger inFlight = new AtomicInteger();
    public final AtomicLong tokensGenerated = new AtomicLong();
    public final AtomicLong rejected = new AtomicLong();

    public StubModel(int port, Behaviour behaviour, long seed) throws IOException {
        this.behaviour = behaviour;
        this.random = new Random(seed);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 512);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/v1/messages", this::messages);
        server.start();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    private void messages(HttpExchange ex) throws IOException {
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        boolean large = !"small".equals(Json.string(body, "model"));
        int maxTokens = (int) Json.number(body, "max_tokens", 256);
        double ttft;
        double u;
        double length;
        double stallDraw;
        synchronized (random) {
            ttft = (large ? 0.7 : 0.3) * Math.exp(0.6 * random.nextGaussian());
            u = random.nextDouble();
            length = 0.5 + 0.5 * random.nextDouble();
            stallDraw = random.nextDouble();
        }
        if (inFlight.incrementAndGet() > behaviour.inFlightLimit()) {
            inFlight.decrementAndGet();
            rejected.incrementAndGet();
            error(ex, 429, "rate_limit_error", "1");
            return;
        }
        try {
            if (u < behaviour.overloadRate()) {
                error(ex, 529, "overloaded_error", null);
                return;
            }
            ex.getResponseHeaders().set("Content-Type", "text/event-stream");
            ex.sendResponseHeaders(200, 0);
            try (OutputStream out = ex.getResponseBody()) {
                sleep(ttft);
                int tokens = Math.max(1, (int) (maxTokens * length));
                double rate = large ? 50 : 150;
                int perChunk = Math.max(1, (int) Math.round(rate * 0.02));
                int sent = 0;
                boolean stall = stallDraw < behaviour.stallRate();
                send(out, "message_start", "{\"type\":\"message_start\"}");
                while (sent < tokens) {
                    int n = Math.min(perChunk, tokens - sent);
                    send(out, "content_block_delta", "{\"type\":\"content_block_delta\",\"tokens\":" + n + "}");
                    sent += n;
                    tokensGenerated.addAndGet(n);
                    if (stall && sent == n) {
                        sleep(behaviour.stallSeconds());
                    } else {
                        sleep(n / rate);
                    }
                }
                send(out, "message_stop", "{\"type\":\"message_stop\",\"usage\":{\"output_tokens\":" + sent + "}}");
            } catch (IOException e) {
                // the client hung up; the provider stops generating
            }
        } finally {
            inFlight.decrementAndGet();
        }
    }

    private static void send(OutputStream out, String event, String data) throws IOException {
        out.write(("event: " + event + "\ndata: " + data + "\n\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private static void error(HttpExchange ex, int status, String type, String retryAfter) throws IOException {
        byte[] b = ("{\"type\":\"error\",\"error\":{\"type\":\"" + type + "\"}}").getBytes(StandardCharsets.UTF_8);
        if (retryAfter != null) {
            ex.getResponseHeaders().set("retry-after", retryAfter);
        }
        ex.sendResponseHeaders(status, b.length);
        ex.getResponseBody().write(b);
        ex.close();
    }

    private static void sleep(double seconds) {
        try {
            Thread.sleep((long) (seconds * 1_000));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
