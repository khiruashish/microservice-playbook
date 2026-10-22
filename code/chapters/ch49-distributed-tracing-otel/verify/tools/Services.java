import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.foodfleet.ch49.baggage.BaggagePolicy;
import dev.foodfleet.ch49.propagation.MessageHeaders;
import dev.foodfleet.ch49.spans.PaymentSpans;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Message;
import io.nats.client.Nats;
import io.nats.client.Subscription;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * FoodFleet's checkout for chapter 49, as separate JVMs under the OpenTelemetry Java agent:
 *
 * <pre>
 *   gateway   POST /orders    -> order-service            (baggage from the edge, sanitized if -Dff.sanitize=true)
 *   order     POST /orders    -> payment-service, then publishes orders.placed on NATS; with -Dff.probes, also
 *                                runs a probe span down eleven ways of handing work to another thread
 *   payment   POST /payments  -> fraud check (a manual span) -> the external payment gateway
 *                                (with no baggage if -Dff.strip=true)
 *   kitchen   NATS orders.placed: push (a dispatcher) or pull (nextMessage), optionally continuing the trace by hand
 *   external  a partner's server, not ours and not instrumented: writes down the headers it receives
 * </pre>
 */
public final class Services {

    static final String SERVICE = System.getProperty("ff.service");
    static final Tracer TRACER = GlobalOpenTelemetry.getTracer("foodfleet.checkout");
    static final HttpClient CLIENT = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(2)).build();
    static final AtomicLong IDS = new AtomicLong(200_000);
    static PrintWriter out;

    public static void main(String[] args) throws Exception {
        String outFile = System.getProperty("ff.out");
        if (outFile != null) {
            out = new PrintWriter(new FileWriter(outFile, StandardCharsets.UTF_8, true), true);
        }
        switch (SERVICE) {
            case "kitchen" -> kitchen();
            default -> http();
        }
    }

    static void http() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", Integer.getInteger("ff.port")), 256);
        server.setExecutor(Executors.newFixedThreadPool(32));
        server.createContext("/health", ex -> respond(ex, 200, "UP"));
        switch (SERVICE) {
            case "gateway" -> server.createContext("/orders", Services::gateway);
            case "order-service" -> server.createContext("/orders", Services::order);
            case "payment-service" -> server.createContext("/payments", Services::payment);
            case "external" -> server.createContext("/charge", Services::external);
            default -> throw new IllegalArgumentException(SERVICE);
        }
        server.start();
    }

    // ------------------------------------------------------------------------------------------------ gateway

    static void gateway(HttpExchange ex) throws IOException {
        Baggage edge = Baggage.current();                               // what the client sent, extracted by the agent
        if (Boolean.getBoolean("ff.sanitize")) {
            edge = BaggagePolicy.sanitize(edge);
        }
        int status;
        try (Scope ignored = Context.current().with(edge).makeCurrent()) {
            status = post(System.getProperty("ff.downstream") + "/orders");
        }
        respond(ex, status == 200 ? 201 : 502, "");
    }

    // ------------------------------------------------------------------------------------------------ order-service

    static final ExecutorService POOL = Executors.newFixedThreadPool(4);
    static final ExecutorService VIRTUAL = Executors.newVirtualThreadPerTaskExecutor();
    static final ScheduledExecutorService SCHEDULER = Executors.newScheduledThreadPool(2);
    static final BlockingQueue<Runnable> QUEUE = new LinkedBlockingQueue<>();
    static Connection nats;

    static {
        Thread worker = new Thread(() -> {                               // a hand-rolled worker, started at boot
            while (true) {
                try {
                    QUEUE.take().run();
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "order-worker");
        worker.setDaemon(true);
        worker.start();
    }

    static void order(HttpExchange ex) throws IOException {
        long id = IDS.incrementAndGet();
        int status = post(System.getProperty("ff.payment") + "/payments?order=" + id);
        if (Boolean.getBoolean("ff.probes")) {
            probes();
        }
        try {
            if (nats == null) {
                synchronized (Services.class) {
                    if (nats == null) {
                        nats = Nats.connect(System.getProperty("ff.nats"));
                    }
                }
            }
            nats.publish("orders.placed", Long.toString(id).getBytes(StandardCharsets.UTF_8));
        } catch (IOException | InterruptedException e) {
            status = 503;
        }
        respond(ex, status, "");
    }

    /** Starts a probe span on each path and writes down whether it landed in the request's trace. */
    static void probes() {
        String trace = Span.current().getSpanContext().getTraceId();
        CountDownLatch done = new CountDownLatch(11);
        probe("same thread", trace, done);
        POOL.submit(() -> probe("ExecutorService (fixed pool)", trace, done));
        CompletableFuture.runAsync(() -> probe("CompletableFuture.runAsync", trace, done));
        new Thread(() -> probe("new Thread(...).start()", trace, done)).start();
        Thread.ofVirtual().start(() -> probe("Thread.ofVirtual().start()", trace, done));
        VIRTUAL.submit(() -> probe("newVirtualThreadPerTaskExecutor()", trace, done));
        SCHEDULER.schedule(() -> probe("ScheduledExecutorService", trace, done), 5, TimeUnit.MILLISECONDS);
        QUEUE.add(() -> probe("own queue + worker thread", trace, done));
        QUEUE.add(Context.current().wrap(() -> probe("own queue, Context.wrap()", trace, done)));
        CLIENT.sendAsync(HttpRequest.newBuilder(URI.create(System.getProperty("ff.payment") + "/health")).build(),
                HttpResponse.BodyHandlers.discarding()).thenRun(() -> probe("HttpClient.sendAsync().thenRun", trace, done));
        new Thread(Context.current().wrap(() -> probe("new Thread, Context.wrap()", trace, done))).start();
        try {
            done.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    static void probe(String path, String requestTrace, CountDownLatch done) {
        Span s = TRACER.spanBuilder("probe").startSpan();
        boolean same = s.getSpanContext().getTraceId().equals(requestTrace);
        s.end();
        out.println(path + "\t" + (same ? "kept" : "lost"));
        done.countDown();
    }

    // ------------------------------------------------------------------------------------------------ payment-service

    static final PaymentSpans SPANS = new PaymentSpans(TRACER);

    static void payment(HttpExchange ex) throws IOException {
        String method = ThreadLocalRandom.current().nextBoolean() ? "upi" : "card";
        SPANS.fraudCheck(method, () -> new PaymentSpans.Decision(
                ThreadLocalRandom.current().nextInt(50) == 0 ? "review" : "approve", 12));
        String partner = System.getProperty("ff.external") + "/charge";
        int status = Boolean.getBoolean("ff.strip") ? BaggagePolicy.withoutBaggage(() -> post(partner)) : post(partner);
        respond(ex, status, "");
    }

    // ------------------------------------------------------------------------------------------------ external partner

    static void external(HttpExchange ex) throws IOException {
        String baggage = ex.getRequestHeaders().getFirst("baggage");
        String traceparent = ex.getRequestHeaders().getFirst("traceparent");
        out.println((baggage == null ? "-" : baggage) + "\t" + (traceparent == null ? "-" : traceparent));
        respond(ex, 200, "");
    }

    // ------------------------------------------------------------------------------------------------ kitchen

    static void kitchen() throws Exception {
        Connection nc = Nats.connect(System.getProperty("ff.nats"));
        String mode = System.getProperty("ff.consume", "push");
        boolean continueTrace = Boolean.getBoolean("ff.extract");
        MessageHeaders headers = MessageHeaders.global();
        if (mode.equals("push")) {
            Dispatcher d = nc.createDispatcher(m -> prepare(m));
            d.subscribe("orders.placed");
        } else {
            Subscription sub = nc.subscribe("orders.placed");
            Thread.ofPlatform().start(() -> {
                while (true) {
                    try {
                        Message m = sub.nextMessage(Duration.ofSeconds(1));
                        if (m == null) {
                            continue;
                        }
                        if (continueTrace && m.getHeaders() != null) {
                            Map<String, String> h = new HashMap<>();
                            m.getHeaders().forEach((k, v) -> h.put(k, v.get(0)));
                            try (Scope ignored = headers.extract(h).makeCurrent()) {
                                prepare(m);
                            }
                        } else {
                            prepare(m);
                        }
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            });
        }
        nc.flush(Duration.ofSeconds(2));
        HttpServer health = HttpServer.create(new InetSocketAddress("127.0.0.1", Integer.getInteger("ff.port")), 16);
        health.createContext("/health", ex -> respond(ex, 200, "UP"));
        health.start();
    }

    static void prepare(Message m) {
        Span s = TRACER.spanBuilder("prepare order").setAttribute("foodfleet.order.id", new String(m.getData(), StandardCharsets.UTF_8))
                .startSpan();
        s.end();
    }

    // ------------------------------------------------------------------------------------------------ http helpers

    static int post(String url) {
        try {
            return CLIENT.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5))
                    .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (IOException e) {
            return 503;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 503;
        }
    }

    static void respond(HttpExchange ex, int status, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.getRequestBody().readAllBytes();
        ex.sendResponseHeaders(status, b.length == 0 ? -1 : b.length);
        if (b.length > 0) {
            ex.getResponseBody().write(b);
        }
        ex.close();
    }
}
