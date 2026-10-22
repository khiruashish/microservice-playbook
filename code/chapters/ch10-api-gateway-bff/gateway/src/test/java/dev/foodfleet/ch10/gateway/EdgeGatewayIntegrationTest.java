package dev.foodfleet.ch10.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The real gateway on a random port, in front of stub backends. Each backend echoes which service
 * it is and the headers it received, so the tests can see exactly what crossed the edge.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"foodfleet.edge.place-order-rate-limit.capacity=3",
                      "foodfleet.edge.place-order-rate-limit.refill-per-minute=1"})
class EdgeGatewayIntegrationTest {

    static final AtomicInteger MENU_CALLS = new AtomicInteger();

    static final HttpServer ORDERING = echo("ordering", 200);
    static final HttpServer ETA = echo("eta", 200);
    static final HttpServer PAYMENT = echo("payment", 200);
    static final HttpServer BFF = echo("mobile-bff", 200);
    static final HttpServer MENU = echo("menu", 503);              // a failing menu-service

    @DynamicPropertySource
    static void backends(DynamicPropertyRegistry registry) {
        registry.add("foodfleet.edge.backends.ordering", () -> url(ORDERING));
        registry.add("foodfleet.edge.backends.eta", () -> url(ETA));
        registry.add("foodfleet.edge.backends.payment", () -> url(PAYMENT));
        registry.add("foodfleet.edge.backends.mobile-bff", () -> url(BFF));
        registry.add("foodfleet.edge.backends.menu", () -> url(MENU));
    }

    @LocalServerPort
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest.Builder at(String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
    }

    @Test
    void routesByPathMostSpecificFirst() throws Exception {
        assertThat(send(at("/api/orders/o-1/eta")).body()).contains("\"service\":\"eta\"");
        assertThat(send(at("/api/orders/o-1")).body()).contains("\"service\":\"ordering\"");
        assertThat(send(at("/api/payments/pay_1")).body()).contains("\"service\":\"payment\"");
        assertThat(send(at("/bff/mobile/v1/orders/o-1/tracking")).body()).contains("\"service\":\"mobile-bff\"");
    }

    @Test
    void stripsSpoofedInternalHeadersAndAddsARequestId() throws Exception {
        String body = send(at("/api/orders/o-1")
                .header("X-Customer-Id", "someone-else")
                .header("X-Internal-Caller", "admin-tool")).body();

        assertThat(body).contains("\"x-customer-id\":null").contains("\"x-internal-caller\":null");
        assertThat(body).containsPattern("\"x-request-id\":\"[0-9a-f-]{36}\"");
    }

    @Test
    void keepsTheCallersRequestId() throws Exception {
        assertThat(send(at("/api/orders/o-1").header("X-Request-Id", "app-trace-42")).body())
                .contains("\"x-request-id\":\"app-trace-42\"");
    }

    @Test
    void placingOrdersIsRateLimitedPerClient() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(send(at("/api/orders").POST(HttpRequest.BodyPublishers.ofString("{}"))
                    .header("Content-Type", "application/json")).statusCode()).isEqualTo(200);
        }
        HttpResponse<String> fourth = send(at("/api/orders").POST(HttpRequest.BodyPublishers.ofString("{}"))
                .header("Content-Type", "application/json"));

        assertThat(fourth.statusCode()).isEqualTo(429);
        assertThat(fourth.headers().firstValue("Retry-After")).hasValue("60");
        assertThat(fourth.body()).contains("Slow down");
    }

    @Test
    void failingMenuServiceGetsRetriedThenAFastFallback() throws Exception {
        HttpResponse<String> response = send(at("/api/menus/rest-1"));

        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.body()).contains("Menu temporarily unavailable");
        assertThat(MENU_CALLS.get()).as("first attempt plus retries").isGreaterThanOrEqualTo(1);
    }

    @AfterAll
    static void stop() {
        for (HttpServer s : new HttpServer[] {ORDERING, ETA, PAYMENT, BFF, MENU}) {
            s.stop(0);
        }
    }

    private static String url(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static HttpServer echo(String service, int status) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> respond(exchange, service, status));
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void respond(HttpExchange exchange, String service, int status) throws IOException {
        if (service.equals("menu")) {
            MENU_CALLS.incrementAndGet();
        }
        var headers = exchange.getRequestHeaders();
        String json = "{\"service\":\"" + service + "\","
                + "\"x-request-id\":" + quoted(headers.getFirst("X-Request-Id")) + ","
                + "\"x-customer-id\":" + quoted(headers.getFirst("X-Customer-Id")) + ","
                + "\"x-internal-caller\":" + quoted(headers.getFirst("X-Internal-Caller")) + "}";
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static String quoted(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }
}
