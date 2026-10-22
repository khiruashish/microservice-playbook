package dev.foodfleet.ch08.eta.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import dev.foodfleet.ch08.eta.domain.EtaCalculator.Eta;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The real HTTP service clients, @Retryable proxy and virtual-thread fan-out, against two tiny
 * stub servers (the JDK's built-in HttpServer, so the test needs no extra dependency).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class EtaServiceIntegrationTest {

    /** Scripted dispatch behaviour: a list of status codes to return, one per call; the last repeats. */
    static final AtomicReference<int[]> DISPATCH_STATUSES = new AtomicReference<>(new int[] {200});
    static final AtomicInteger DISPATCH_CALLS = new AtomicInteger();
    static final AtomicInteger KITCHEN_STATUS = new AtomicInteger(200);

    static final HttpServer KITCHEN = start(exchange -> {
        int status = KITCHEN_STATUS.get();
        respond(exchange, status, status == 200 ? "{\"orderId\":\"o-1\",\"minutesRemaining\":12,\"station\":\"tandoor\"}" : "{}");
    });

    static final HttpServer DISPATCH = start(exchange -> {
        int call = DISPATCH_CALLS.getAndIncrement();
        int[] statuses = DISPATCH_STATUSES.get();
        int status = statuses[Math.min(call, statuses.length - 1)];
        respond(exchange, status, status == 200
                ? "{\"riderId\":\"rider-7\",\"minutesToRestaurant\":8,\"minutesToCustomer\":15}"
                : "{}");
    });

    @DynamicPropertySource
    static void downstreamUrls(DynamicPropertyRegistry registry) {
        registry.add("spring.http.serviceclient.kitchen.base-url", () -> "http://127.0.0.1:" + KITCHEN.getAddress().getPort());
        registry.add("spring.http.serviceclient.dispatch.base-url", () -> "http://127.0.0.1:" + DISPATCH.getAddress().getPort());
    }

    @Autowired
    EtaService etas;

    @BeforeEach
    void reset() {
        DISPATCH_STATUSES.set(new int[] {200});
        DISPATCH_CALLS.set(0);
        KITCHEN_STATUS.set(200);
    }

    @Test
    void liveDataFromBothServices() {
        Eta eta = etas.etaFor("o-1");

        assertThat(eta.minutes()).isEqualTo(27);
        assertThat(eta.degraded()).isFalse();
        assertThat(DISPATCH_CALLS).hasValue(1);
    }

    @Test
    void transientDispatchErrorsAreRetried() {
        DISPATCH_STATUSES.set(new int[] {503, 502, 200});

        Eta eta = etas.etaFor("o-1");

        assertThat(eta.degraded()).isFalse();
        assertThat(DISPATCH_CALLS).as("1 attempt + 2 retries").hasValue(3);
    }

    @Test
    void dispatchDownDegradesInsteadOfFailing() {
        DISPATCH_STATUSES.set(new int[] {500});

        Eta eta = etas.etaFor("o-1");

        assertThat(eta.degraded()).isTrue();
        assertThat(eta.window().display()).isEqualTo("24–40 min");
        assertThat(DISPATCH_CALLS).hasValue(3);
    }

    @Test
    void noRiderYetIsAnAnswerNotAFailure_soNoRetries() {
        DISPATCH_STATUSES.set(new int[] {404});

        assertThat(etas.etaFor("o-1").degraded()).isTrue();
        assertThat(DISPATCH_CALLS).hasValue(1);
    }

    @Test
    void kitchenDownFailsTheRequest() {
        KITCHEN_STATUS.set(500);

        assertThatThrownBy(() -> etas.etaFor("o-1")).isInstanceOf(KitchenUnavailableException.class);
    }

    @AfterAll
    static void stop() {
        KITCHEN.stop(0);
        DISPATCH.stop(0);
    }

    private static HttpServer start(com.sun.net.httpserver.HttpHandler handler) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", handler);
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
