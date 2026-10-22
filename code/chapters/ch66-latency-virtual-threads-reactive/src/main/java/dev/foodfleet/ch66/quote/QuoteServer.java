package dev.foodfleet.ch66.quote;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

/**
 * order-service's quote endpoint: to quote an order it asks menu-service for the price, pricing-service for the
 * discount and delivery-service for the ETA, one after the other, then answers. The same work in four concurrency
 * models:
 * <ul>
 *   <li>{@code platform}: blocking calls on a pool of 200 platform threads (Tomcat's default maximum)</li>
 *   <li>{@code virtual}: the same blocking code, one virtual thread per request
 *       ({@code spring.threads.virtual.enabled=true})</li>
 *   <li>{@code async}: non-blocking calls chained with CompletableFuture on two event-loop threads, the way WebFlux on
 *       Reactor Netty runs: no thread waits for I/O</li>
 *   <li>{@code virtual-limited}: virtual threads with at most 300 requests in progress; beyond that, an immediate 503
 *       (a bulkhead, chapter 44), because nothing else limits how many virtual threads pile up</li>
 * </ul>
 */
public final class QuoteServer {

    private QuoteServer() {
    }

    public static HttpServer start(String model, int port, String backend) throws IOException {
        System.setProperty("sun.net.httpserver.nodelay", "true");
        // the JDK's server keeps at most 200 idle keep-alive connections by default and closes the rest, which a
        // client then finds closed when it reuses them; a busy caller holds more than that
        System.setProperty("sun.net.httpserver.maxIdleConnections", "4000");
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 8192);
        String[] calls = {backend + "/menu/7/items/paneer-tikka", backend + "/pricing/FIRST20", backend + "/delivery/eta?restaurant=7"};
        switch (model) {
            case "platform", "virtual", "virtual-limited" -> {
                ExecutorService executor = model.equals("platform") ? Executors.newFixedThreadPool(200, Thread.ofPlatform().daemon().factory())
                        : Executors.newVirtualThreadPerTaskExecutor();
                HttpClient http = HttpClient.newBuilder().executor(Executors.newVirtualThreadPerTaskExecutor()).build();
                Semaphore inProgress = new Semaphore(model.equals("virtual-limited") ? 300 : Integer.MAX_VALUE);
                server.setExecutor(executor);
                server.createContext("/quote", ex -> {
                    if (!inProgress.tryAcquire()) {
                        try (ex) {
                            ex.sendResponseHeaders(503, -1);
                        }
                        return;
                    }
                    try (ex) {
                        int status = 200;
                        for (String url : calls) {
                            if (http.send(request(url), HttpResponse.BodyHandlers.discarding()).statusCode() != 200) {
                                status = 502;
                            }
                        }
                        respond(ex, status);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        inProgress.release();
                    }
                });
            }
            case "async" -> {
                ExecutorService loop = Executors.newFixedThreadPool(2, Thread.ofPlatform().daemon().factory());
                HttpClient http = HttpClient.newBuilder().executor(loop).build();
                server.setExecutor(loop);
                server.createContext("/quote", ex -> {
                    CompletableFuture<Integer> chain = CompletableFuture.completedFuture(200);
                    for (String url : calls) {
                        chain = chain.thenCompose(status -> http.sendAsync(request(url), HttpResponse.BodyHandlers.discarding())
                                .thenApply(r -> r.statusCode() == 200 ? status : 502));
                    }
                    chain.whenComplete((status, error) -> {
                        try (ex) {
                            respond(ex, error == null ? status : 502);
                        }
                    });
                    // the handler returns at once; the exchange is completed by the last callback
                });
            }
            default -> throw new IllegalArgumentException(model);
        }
        server.start();
        return server;
    }

    static HttpRequest request(String url) {
        return HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).build();
    }

    static void respond(HttpExchange ex, int status) {
        try {
            byte[] b = "{\"total\":\"103.60\",\"eta\":\"25 min\"}".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status, b.length);
            ex.getResponseBody().write(b);
        } catch (IOException e) {
            // client went away
        }
    }

    /** {@code QuoteServer <model> <port> <backend url>}: runs until killed. */
    public static void main(String[] args) throws IOException {
        start(args[0], Integer.parseInt(args[1]), args[2]);
    }
}
