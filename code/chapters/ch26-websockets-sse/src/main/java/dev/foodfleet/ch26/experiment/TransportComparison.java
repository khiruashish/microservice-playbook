package dev.foodfleet.ch26.experiment;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * One tracking screen, four transports, over real loopback sockets through a byte-counting proxy.
 * The rider's position is published 40 times, every 250 ms (10 s). Every HTTP request carries a
 * realistic 700-character bearer token, as the app's would. Polling asks every second.
 * "Staleness" is, for each published position, the time until the app had it or a newer one:
 * how out of date the rider on the map was.
 */
public final class TransportComparison {

    public enum Transport { SHORT_POLLING_1S, LONG_POLLING, SERVER_SENT_EVENTS, WEBSOCKET }

    public record Result(Transport transport, int requests, int connections, long bytesUp, long bytesDown,
                         int updatesSeen, int updatesPublished, double meanStalenessMs, double p95StalenessMs) {
        public double bytesPerUpdateSeen() {
            return (bytesUp + bytesDown) / (double) Math.max(1, updatesSeen);
        }
    }

    static final int UPDATES = 40;
    static final long INTERVAL_MS = 250;
    static final String TOKEN = "Bearer " + "x".repeat(693);
    static final Pattern SEQ = Pattern.compile("\"seq\":(\\d+)");

    public static Result run(Transport transport) throws Exception {
        PositionFeed feed = new PositionFeed();
        java.util.Map<Integer, Long> receivedAt = new java.util.concurrent.ConcurrentHashMap<>();
        int[] requests = {0};
        AtomicBoolean running = new AtomicBoolean(true);

        try (TrackingHttpServer http = new TrackingHttpServer(feed);
             MiniWebSocketServer ws = new MiniWebSocketServer(feed);
             ByteCountingProxy proxy = new ByteCountingProxy(transport == Transport.WEBSOCKET ? ws.port() : http.port())) {
            // Not try-with-resources: close() would wait for the open stream; shutdownNow() below aborts it.
            HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

            String base = "http://127.0.0.1:" + proxy.port();
            java.util.function.Consumer<String> onPayload = body -> {
                long now = System.nanoTime();
                Matcher m = SEQ.matcher(body);
                while (m.find()) {
                    receivedAt.putIfAbsent(Integer.parseInt(m.group(1)), now);
                }
            };

            Thread clientThread = null;
            WebSocket socket = null;
            switch (transport) {
                case SHORT_POLLING_1S, LONG_POLLING -> {
                    String path = transport == Transport.SHORT_POLLING_1S ? "/poll" : "/long-poll";
                    clientThread = Thread.ofVirtual().start(() -> {
                        int last = 0;
                        while (running.get()) {
                            try {
                                HttpResponse<String> r = client.send(HttpRequest.newBuilder(URI.create(base + path + "?since=" + last))
                                        .header("Authorization", TOKEN).timeout(Duration.ofSeconds(30)).build(),
                                        HttpResponse.BodyHandlers.ofString());
                                requests[0]++;
                                if (r.statusCode() == 200) {
                                    onPayload.accept(r.body());
                                    Matcher m = SEQ.matcher(r.body());
                                    while (m.find()) {
                                        last = Math.max(last, Integer.parseInt(m.group(1)));
                                    }
                                }
                                if (transport == Transport.SHORT_POLLING_1S) {
                                    Thread.sleep(1_000);
                                }
                            } catch (Exception e) {
                                return;
                            }
                        }
                    });
                }
                case SERVER_SENT_EVENTS -> clientThread = Thread.ofVirtual().start(() -> {
                    try {
                        requests[0]++;
                        HttpResponse<Stream<String>> r = client.send(HttpRequest.newBuilder(URI.create(base + "/sse"))
                                .header("Authorization", TOKEN).header("Accept", "text/event-stream").build(),
                                HttpResponse.BodyHandlers.ofLines());
                        r.body().takeWhile(line -> running.get()).filter(l -> l.startsWith("data: ")).forEach(onPayload);
                    } catch (Exception ignored) {
                        // stream closed at the end
                    }
                });
                case WEBSOCKET -> {
                    requests[0]++;
                    socket = client.newWebSocketBuilder().header("Authorization", TOKEN)
                            .buildAsync(URI.create("ws://127.0.0.1:" + proxy.port() + "/ws"), new WebSocket.Listener() {
                                final StringBuilder text = new StringBuilder();

                                @Override
                                public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                                    text.append(data);
                                    if (last) {
                                        onPayload.accept(text.toString());
                                        text.setLength(0);
                                    }
                                    webSocket.request(1);
                                    return null;
                                }
                            }).join();
                }
            }

            Thread.sleep(300);                                            // connect, first request
            for (int seq = 1; seq <= UPDATES; seq++) {
                feed.publish(seq);
                Thread.sleep(INTERVAL_MS);
            }
            Thread.sleep(1_200);                                          // let the last poll happen
            running.set(false);
            if (socket != null) {
                socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
                Thread.sleep(100);
            }
            feed.publish(UPDATES + 1);                                    // wake the long-poller / SSE reader
            Thread.sleep(200);
            if (clientThread != null) {
                clientThread.interrupt();
            }
            client.shutdownNow();
            // Staleness: for each published position, how long until the app had it or a newer one.
            List<Double> staleness = new ArrayList<>();
            for (int seq = 1; seq <= UPDATES; seq++) {
                long published = feed.get(seq).publishedNanos();
                long firstNewer = Long.MAX_VALUE;
                for (var e : receivedAt.entrySet()) {
                    if (e.getKey() >= seq && e.getKey() <= UPDATES) {
                        firstNewer = Math.min(firstNewer, e.getValue());
                    }
                }
                if (firstNewer != Long.MAX_VALUE) {
                    staleness.add((firstNewer - published) / 1e6);
                }
            }
            Collections.sort(staleness);
            long seenCount = receivedAt.keySet().stream().filter(s -> s <= UPDATES).count();
            double mean = staleness.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double p95 = staleness.isEmpty() ? 0 : staleness.get((int) Math.min(staleness.size() - 1, staleness.size() * 0.95));
            int countedRequests = transport == Transport.LONG_POLLING ? requests[0] - 1 : requests[0];  // drop the wake-up
            return new Result(transport, Math.max(1, countedRequests), proxy.connections(), proxy.bytesUp(),
                    proxy.bytesDown(), (int) seenCount, UPDATES, mean, p95);
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("one tracking screen: 40 position updates, one every 250 ms; 700-char bearer token per request\n");
        System.out.printf("%-20s %9s %6s %9s %10s %13s %12s %10s %9s%n", "transport", "requests", "conns",
                "bytes up", "bytes down", "updates seen", "bytes/update", "stale mean", "stale p95");
        run(Transport.SERVER_SENT_EVENTS);                                 // warm-up (JIT, sockets)
        for (Transport t : args.length > 0 ? new Transport[] {Transport.valueOf(args[0])} : Transport.values()) {
            Result r = run(t);
            System.out.printf("%-20s %9d %6d %9d %10d %10d/%d %12.0f %10.1f %9.1f%n", t, r.requests(), r.connections(),
                    r.bytesUp(), r.bytesDown(), r.updatesSeen(), r.updatesPublished(), r.bytesPerUpdateSeen(),
                    r.meanStalenessMs(), r.p95StalenessMs());
        }
    }
}
