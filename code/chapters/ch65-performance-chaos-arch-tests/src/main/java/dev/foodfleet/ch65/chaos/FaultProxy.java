package dev.foodfleet.ch65.chaos;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A proxy in front of a dependency that injects faults on command, as Toxiproxy (latency, timeouts, resets), a service
 * mesh's fault injection or Chaos Mesh do. Faults apply to a fraction of requests.
 */
public final class FaultProxy implements AutoCloseable {

    public sealed interface Fault permits None, Latency, Errors, Down {
    }

    public record None() implements Fault {
    }

    /** {@code millis} added to {@code fraction} of requests. */
    public record Latency(long millis, double fraction) implements Fault {
    }

    /** 503 for {@code fraction} of requests. */
    public record Errors(double fraction) implements Fault {
    }

    /** The dependency is gone: connections are accepted and nothing ever comes back, until the fault ends. */
    public record Down() implements Fault {
    }

    private final HttpServer server;
    private final HttpClient upstream = HttpClient.newBuilder().executor(Executors.newVirtualThreadPerTaskExecutor()).build();
    private volatile Fault fault = new None();
    private final Object healed = new Object();

    public FaultProxy(String target) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 4096);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/", ex -> {
            try (ex) {
                Fault f = fault;
                double roll = ThreadLocalRandom.current().nextDouble();
                switch (f) {
                    case Latency l when roll < l.fraction() -> Thread.sleep(l.millis());
                    case Errors e when roll < e.fraction() -> {
                        ex.sendResponseHeaders(503, -1);
                        return;
                    }
                    case Down d -> {
                        synchronized (healed) {
                            while (fault instanceof Down) {
                                healed.wait();
                            }
                        }
                        ex.sendResponseHeaders(503, -1);
                        return;
                    }
                    default -> {
                    }
                }
                HttpResponse<byte[]> r = upstream.send(HttpRequest.newBuilder(URI.create(target + ex.getRequestURI())).timeout(Duration.ofSeconds(30)).build(),
                        HttpResponse.BodyHandlers.ofByteArray());
                ex.sendResponseHeaders(r.statusCode(), r.body().length == 0 ? -1 : r.body().length);
                if (r.body().length > 0) {
                    ex.getResponseBody().write(r.body());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                byte[] b = "{\"error\":\"upstream\"}".getBytes(StandardCharsets.UTF_8);
                try {
                    ex.sendResponseHeaders(502, b.length);
                    ex.getResponseBody().write(b);
                } catch (IOException ignored) {
                    // client went away
                }
            }
        });
        server.start();
    }

    public void inject(Fault f) {
        fault = f;
        synchronized (healed) {
            healed.notifyAll();
        }
    }

    public String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Override
    public void close() {
        inject(new None());
        server.stop(0);
    }
}
