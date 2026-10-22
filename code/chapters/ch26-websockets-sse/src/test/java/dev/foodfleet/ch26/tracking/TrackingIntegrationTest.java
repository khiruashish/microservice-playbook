package dev.foodfleet.ch26.tracking;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * The real server on a random port, with the JDK's HTTP and WebSocket clients: a rider's position
 * sent over the WebSocket reaches a customer's SSE stream; a reconnect with Last-Event-ID gets what
 * was missed; a handshake without the gateway's rider header is refused.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TrackingIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    TrackingHub hub;

    final HttpClient client = HttpClient.newHttpClient();

    @AfterEach
    void stop() {
        client.shutdownNow();                                    // close() would wait for open streams
    }

    @Test
    void aRiderPositionReachesTheCustomersStream() throws Exception {
        BlockingQueue<String> lines = openStream("o-7001", null);
        awaitListener("o-7001");
        BlockingQueue<String> acks = new LinkedBlockingQueue<>();
        WebSocket rider = connectRider("d-31", acks);

        rider.sendText("{\"orderId\":\"o-7001\",\"lat\":12.935192,\"lng\":77.624481}", true).join();

        assertThat(acks.poll(5, TimeUnit.SECONDS)).isEqualTo("{\"ack\":1}");
        assertThat(nextData(lines)).contains("\"riderId\":\"d-31\"").contains("\"lat\":12.935192");
        rider.sendClose(WebSocket.NORMAL_CLOSURE, "shift over").join();
    }

    @Test
    void aReconnectingClientGetsWhatItMissed() throws Exception {
        hub.publish("o-7002", "{\"n\":1}");
        hub.publish("o-7002", "{\"n\":2}");
        hub.publish("o-7002", "{\"n\":3}");

        BlockingQueue<String> lines = openStream("o-7002", "1");                // saw event 1, then lost signal

        assertThat(nextData(lines)).isEqualTo("{\"n\":2}");
        assertThat(nextData(lines)).isEqualTo("{\"n\":3}");
    }

    @Test
    void aRiderWithoutTheGatewayHeaderIsRefused() {
        CompletionException failure = catchCompletion(() -> client.newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:" + port + "/ws/rider"), new WebSocket.Listener() { }).join());

        assertThat(failure.getCause()).isInstanceOf(WebSocketHandshakeException.class);
        assertThat(((WebSocketHandshakeException) failure.getCause()).getResponse().statusCode()).isEqualTo(401);
    }

    @Test
    void malformedRiderMessagesCloseTheSession() throws Exception {
        BlockingQueue<String> events = new LinkedBlockingQueue<>();
        WebSocket rider = client.newWebSocketBuilder().header("X-Rider-Id", "d-32")
                .buildAsync(URI.create("ws://localhost:" + port + "/ws/rider"), new WebSocket.Listener() {
                    @Override
                    public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
                        events.add(statusCode + " " + reason);
                        return null;
                    }
                }).join();

        rider.sendText("{\"lat\":\"not a number\"}", true).join();

        assertThat(events.poll(5, TimeUnit.SECONDS)).startsWith("1007");
    }

    private static CompletionException catchCompletion(Runnable call) {
        try {
            call.run();
        } catch (CompletionException e) {
            return e;
        }
        throw new AssertionError("expected the handshake to fail");
    }

    private BlockingQueue<String> openStream(String orderId, String lastEventId) {
        BlockingQueue<String> lines = new LinkedBlockingQueue<>();
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/orders/" + orderId + "/tracking"))
                .header("Accept", "text/event-stream");
        if (lastEventId != null) {
            request.header("Last-Event-ID", lastEventId);
        }
        client.sendAsync(request.build(), HttpResponse.BodyHandlers.ofLines())
                .thenAccept(response -> {
                    try (Stream<String> body = response.body()) {
                        body.forEach(lines::add);
                    }
                });
        return lines;
    }

    private WebSocket connectRider(String riderId, BlockingQueue<String> received) {
        return client.newWebSocketBuilder().header("X-Rider-Id", riderId)
                .buildAsync(URI.create("ws://localhost:" + port + "/ws/rider"), new WebSocket.Listener() {
                    @Override
                    public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
                        received.add(data.toString());
                        ws.request(1);
                        return null;
                    }
                }).join();
    }

    private static String nextData(BlockingQueue<String> lines) throws InterruptedException {
        long until = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < until) {
            String line = lines.poll(100, TimeUnit.MILLISECONDS);
            if (line != null && line.startsWith("data:")) {
                return line.substring(5).trim();
            }
        }
        throw new AssertionError("no data line within 5 s");
    }

    private void awaitListener(String orderId) throws InterruptedException {
        long until = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (hub.listenerCount(orderId) == 0 && System.nanoTime() < until) {
            Thread.sleep(20);
        }
        assertThat(hub.listenerCount(orderId)).isEqualTo(1);
    }
}
