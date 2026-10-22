import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.foodfleet.ch47.correlation.TraceParent;
import dev.foodfleet.ch47.events.WideEvent;
import dev.foodfleet.ch47.events.WideEventWriter;

import java.io.FileWriter;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * FoodFleet's checkout path as three small services on the JDK's HTTP server and client, so that the OpenTelemetry
 * Java agent instruments them without any code (its javahttpserver and javahttpclient instrumentations):
 *
 * <pre>
 *   gateway  POST /orders    -> order-service
 *   order    POST /orders    -> payment-service (a random one of the "pod url" lines in a file, re-read every 500 ms)
 *   payment  POST /payments  -> 2–8 ms of "database" work
 * </pre>
 *
 * Request headers x-tenant, x-payment-method and x-bank travel with the request. Each service writes a text log line
 * per request through java.util.logging (what most services log) and, if -Dff.events is set, a wide event per request.
 *
 * Payment-service 1.5 added a fraud check that, for UPI payments through bank-x, waits 600 ms on a slow
 * dependency: the chapter's gray failure. -Dff.hotspot.iterations hashes each idempotency key with PBKDF2 (the
 * profile experiment's CPU hot spot).
 *
 * Usage: java [-javaagent:...] -Dff.service=... -Dff.version=... -Dff.pod=... -Dff.port=... -Dff.downstream=...
 *        Services
 */
public final class Services {

    private static final Logger LOG = Logger.getLogger("foodfleet");
    private static final String SERVICE = System.getProperty("ff.service");
    private static final String VERSION = System.getProperty("ff.version", "1.4");
    private static final String POD = System.getProperty("ff.pod", SERVICE + "-0");
    private static final int HOTSPOT = Integer.getInteger("ff.hotspot.iterations", 0);
    private static final AtomicLong ORDER_IDS = new AtomicLong(100_000);
    private static final HttpClient CLIENT = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(2)).executor(Executors.newVirtualThreadPerTaskExecutor()).build();

    private static WideEventWriter events;
    private static volatile List<String> payments = List.of();

    public static void main(String[] args) throws Exception {
        int port = Integer.getInteger("ff.port");
        String downstream = System.getProperty("ff.downstream", "");
        String eventsFile = System.getProperty("ff.events");
        if (eventsFile != null) {
            events = new WideEventWriter(new FileWriter(eventsFile, StandardCharsets.UTF_8, true));
            Runtime.getRuntime().addShutdownHook(new Thread(events::close));
            Thread.ofPlatform().daemon().start(() -> {
                while (true) {
                    sleep(1000);
                    events.flush();
                }
            });
        }
        if (downstream.startsWith("file:")) {
            Path instances = Path.of(downstream.substring(5));
            Thread.ofPlatform().daemon().start(() -> {
                while (true) {
                    try {
                        payments = Files.readAllLines(instances).stream().filter(l -> !l.isBlank()).toList();
                    } catch (IOException ignored) {
                        // keep the last list
                    }
                    sleep(500);
                }
            });
        }
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 512);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/health", ex -> respond(ex, 200, "UP"));
        switch (SERVICE) {
            case "gateway" -> server.createContext("/orders", ex -> gateway(ex, downstream));
            case "order-service" -> server.createContext("/orders", Services::order);
            case "payment-service" -> server.createContext("/payments", Services::payment);
            default -> throw new IllegalArgumentException(SERVICE);
        }
        server.start();
        LOG.info(SERVICE + " " + VERSION + " started on port " + port);
    }

    private static void gateway(HttpExchange ex, String orderService) throws IOException {
        int status = forward(ex, orderService + "/orders");
        respond(ex, status, status == 201 ? "created" : "failed");
    }

    private static void order(HttpExchange ex) throws IOException {
        WideEvent e = start(ex, "POST /orders");
        long id = ORDER_IDS.incrementAndGet();
        List<String> instances = payments;
        String[] target = instances.get(ThreadLocalRandom.current().nextInt(instances.size())).split(" ");
        e.with("order.id", id).with("payment.pod", target[0]);
        int status = forward(ex, target[1] + "/payments?order=" + id);
        LOG.info("Order " + id + " placed for " + header(ex, "x-tenant"));
        finish(e, status == 200 ? "ok" : "payment_failed");
        respond(ex, status == 200 ? 201 : 502, "order " + id);
    }

    private static void payment(HttpExchange ex) throws IOException {
        WideEvent e = start(ex, "POST /payments");
        String query = ex.getRequestURI().getQuery();
        String order = query == null ? "?" : query.substring(query.indexOf('=') + 1);
        String method = header(ex, "x-payment-method");
        String bank = header(ex, "x-bank");
        if (HOTSPOT > 0) {
            e.with("idempotency.key", idempotencyKey(order));
        }
        sleep(2 + ThreadLocalRandom.current().nextInt(7));           // the payments database
        boolean fraudCheck = "1.5".equals(VERSION) && "UPI".equals(method) && "bank-x".equals(bank);
        if (fraudCheck) {
            sleep(600);                                               // 1.5's new fraud check, slow for this bank
        }
        LOG.info("Authorised payment for order " + order);
        finish(e, "ok");
        respond(ex, 200, "authorised");
    }

    /** The profile experiment's hot spot: a password-hashing function used to derive an idempotency key. */
    private static String idempotencyKey(String order) {
        try {
            PBEKeySpec spec = new PBEKeySpec(order.toCharArray(), "foodfleet".getBytes(StandardCharsets.UTF_8), HOTSPOT, 128);
            return HexFormat.of().formatHex(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static WideEvent start(HttpExchange ex, String operation) {
        WideEvent e = WideEvent.start(SERVICE, operation)
                .with("service.version", VERSION).with("pod", POD)
                .with("tenant", header(ex, "x-tenant"))
                .with("payment.method", header(ex, "x-payment-method"))
                .with("payment.bank", header(ex, "x-bank"));
        TraceParent.parse(ex.getRequestHeaders().getFirst("traceparent")).ifPresent(t -> e.with("trace_id", t.traceId()));
        return e;
    }

    private static void finish(WideEvent e, String outcome) {
        if (events != null) {
            events.write(e.finish(outcome));
        }
    }

    private static int forward(HttpExchange ex, String url) {
        HttpRequest.Builder r = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5))
                .POST(HttpRequest.BodyPublishers.noBody());
        for (String h : List.of("x-tenant", "x-payment-method", "x-bank")) {
            String v = header(ex, h);
            if (v != null) {
                r.header(h, v);
            }
        }
        try {
            return CLIENT.send(r.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (IOException e) {
            return 503;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 503;
        }
    }

    private static String header(HttpExchange ex, String name) {
        return ex.getRequestHeaders().getFirst(name);
    }

    private static void respond(HttpExchange ex, int status, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.getRequestBody().readAllBytes();
        ex.sendResponseHeaders(status, b.length);
        ex.getResponseBody().write(b);
        ex.close();
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
