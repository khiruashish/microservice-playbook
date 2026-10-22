package dev.foodfleet.ch10.bff.tracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.HttpClientErrorException;

/**
 * The real HTTP service clients and fan-out against one stub server that plays all four services.
 * Each test scripts the stub: path → (status, body), plus an optional delay for one path.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class TrackingScreenIntegrationTest {

    record Reply(int status, String body, long delayMillis) {
    }

    static final Map<String, Reply> SCRIPT = new ConcurrentHashMap<>();
    static final Map<String, String> LAST_HEADERS = new ConcurrentHashMap<>();

    static final HttpServer STUB = start();

    @DynamicPropertySource
    static void urls(DynamicPropertyRegistry registry) {
        String url = "http://127.0.0.1:" + STUB.getAddress().getPort();
        for (String group : new String[] {"ordering", "eta", "catalog", "dispatch"}) {
            registry.add("spring.http.serviceclient." + group + ".base-url", () -> url);
        }
    }

    @Autowired
    TrackingScreenService screens;

    @BeforeEach
    void happyPath() {
        SCRIPT.clear();
        SCRIPT.put("/api/orders/o-1", new Reply(200, """
                {"orderId":"o-1","restaurantId":"rest-1","status":"PICKED_UP","itemCount":3,"totalPaise":87185,
                 "lines":[{"dish":"Dum Biryani","qty":2}],"customer":{"id":"c-42"}}""", 0));
        SCRIPT.put("/api/orders/o-1/eta", new Reply(200, """
                {"orderId":"o-1","window":{"from":6,"to":12,"display":"6–12 min"},
                 "estimatedArrival":"2026-10-01T14:57:00Z","degraded":false}""", 0));
        SCRIPT.put("/api/restaurants/rest-1", new Reply(200, """
                {"restaurantId":"rest-1","name":"Biryani House","area":"Koramangala","fssaiLicence":"1001"}""", 0));
        SCRIPT.put("/api/v1/dispatch/orders/o-1/rider", new Reply(200, """
                {"riderId":"rider-7","name":"Ravi","vehicle":"Scooter KA-01-AB-1234","lat":12.93,"lon":77.62}""", 0));
    }

    @Test
    void aggregatesFourServicesIntoOneCard() {
        TrackingCard card = screens.trackingCard("o-1");

        assertThat(card.title()).isEqualTo("On the way");
        assertThat(card.subtitle()).isEqualTo("from Biryani House, Koramangala");
        assertThat(card.rider().name()).isEqualTo("Ravi");
        assertThat(card.etaText()).startsWith("Arriving ");
        assertThat(card.degraded()).isEmpty();
        assertThat(LAST_HEADERS.get("/api/orders/o-1/eta")).as("API version sent to eta-service").isEqualTo("2.0");
    }

    @Test
    void aSlowEtaDegradesOnlyTheEta() {
        SCRIPT.put("/api/orders/o-1/eta", new Reply(200, "{}", 2_000));       // longer than the 600 ms budget

        TrackingCard card = screens.trackingCard("o-1");

        assertThat(card.etaText()).isNull();
        assertThat(card.degraded()).containsExactly("eta");
        assertThat(card.rider()).isNotNull();
    }

    @Test
    void noRiderYetIsNotAFailure() {
        SCRIPT.put("/api/v1/dispatch/orders/o-1/rider", new Reply(404, "{}", 0));

        TrackingCard card = screens.trackingCard("o-1");

        assertThat(card.rider()).isNull();
        assertThat(card.degraded()).isEmpty();
    }

    @Test
    void withoutTheOrderThereIsNoScreen() {
        SCRIPT.put("/api/orders/o-1", new Reply(404, "{}", 0));

        assertThatThrownBy(() -> screens.trackingCard("o-1")).isInstanceOf(HttpClientErrorException.NotFound.class);
    }

    @AfterAll
    static void stop() {
        STUB.stop(0);
    }

    private static HttpServer start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", TrackingScreenIntegrationTest::handle);
            server.setExecutor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String version = exchange.getRequestHeaders().getFirst("API-Version");
        LAST_HEADERS.put(path, version == null ? "" : version);
        Reply reply = SCRIPT.getOrDefault(path, new Reply(404, "{}", 0));
        if (reply.delayMillis() > 0) {
            try {
                Thread.sleep(reply.delayMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(reply.status(), bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
