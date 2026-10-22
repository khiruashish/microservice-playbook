package dev.foodfleet.ch26.experiment;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * The HTTP side of the experiment (JDK HttpServer on virtual threads):
 * <ul>
 *   <li>{@code GET /poll?since=n}: the latest position if newer than n (200), else 204;</li>
 *   <li>{@code GET /long-poll?since=n}: waits up to 25 s for anything newer, returns all of it;</li>
 *   <li>{@code GET /sse}: a {@code text/event-stream}, resuming after {@code Last-Event-ID}.</li>
 * </ul>
 */
public final class TrackingHttpServer implements AutoCloseable {

    static {
        System.setProperty("sun.net.httpserver.nodelay", "true");     // no Nagle delay on small writes
    }

    private final HttpServer server;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final PositionFeed feed;

    public TrackingHttpServer(PositionFeed feed) throws IOException {
        this.feed = feed;
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setExecutor(executor);
        server.createContext("/poll", this::poll);
        server.createContext("/long-poll", this::longPoll);
        server.createContext("/sse", this::sse);
        server.start();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    private void poll(HttpExchange ex) throws IOException {
        PositionFeed.Update latest = feed.latest();
        if (latest == null || latest.seq() <= since(ex)) {
            ex.sendResponseHeaders(204, -1);
            ex.close();
            return;
        }
        json(ex, "[" + latest.json() + "]");
    }

    private void longPoll(HttpExchange ex) throws IOException {
        try {
            List<PositionFeed.Update> updates = feed.awaitSince(since(ex), 25_000);
            if (updates.isEmpty()) {
                ex.sendResponseHeaders(204, -1);
                ex.close();
                return;
            }
            json(ex, updates.stream().map(PositionFeed.Update::json).collect(Collectors.joining(",", "[", "]")));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            ex.close();
        }
    }

    private void sse(HttpExchange ex) throws IOException {
        String lastId = ex.getRequestHeaders().getFirst("Last-Event-ID");
        int after = lastId == null ? 0 : Integer.parseInt(lastId);
        ex.getResponseHeaders().set("Content-Type", "text/event-stream");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(200, 0);                                  // chunked, open-ended
        LinkedBlockingQueue<PositionFeed.Update> queue = new LinkedBlockingQueue<>();
        Runnable unsubscribe = feed.subscribe(queue::add);
        try (OutputStream out = ex.getResponseBody()) {
            for (PositionFeed.Update missed : feed.since(after)) {         // resume after a reconnect
                queue.add(missed);
            }
            int lastSent = after;
            while (true) {
                PositionFeed.Update u = queue.poll(15, TimeUnit.SECONDS);
                String frame;
                if (u == null) {
                    frame = ": keep-alive\n\n";                              // comment line: keeps proxies from closing
                } else if (u.seq() <= lastSent) {
                    continue;                                              // already sent from the backlog
                } else {
                    frame = "id: " + u.seq() + "\ndata: " + u.json() + "\n\n";
                    lastSent = u.seq();
                }
                out.write(frame.getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
        } catch (IOException | InterruptedException e) {
            // client went away
        } finally {
            unsubscribe.run();
        }
    }

    private static int since(HttpExchange ex) {
        String q = ex.getRequestURI().getQuery();
        return q != null && q.startsWith("since=") ? Integer.parseInt(q.substring(6)) : 0;
    }

    private static void json(HttpExchange ex, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }
}
