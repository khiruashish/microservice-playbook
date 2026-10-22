package dev.foodfleet.ch61;

import com.sun.net.httpserver.HttpServer;
import dev.foodfleet.ch61.order.HttpMenuClient;
import dev.foodfleet.ch61.order.JdbcOrderRepository;
import dev.foodfleet.ch61.order.OrderApi;
import dev.foodfleet.ch61.order.OrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Component tests: the whole service over HTTP, with PostgreSQL and menu-service as the contract describes it. */
@Tag("component")
class OrderApiComponentTest {

    MenuStub menu;
    HttpServer server;
    String base;
    final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void start() throws Exception {
        menu = new MenuStub();
        JdbcOrderRepository repo = new JdbcOrderRepository(TestDatabases.postgres());
        server = new OrderApi(new OrderService(new HttpMenuClient(menu.url()), repo), repo).start(0);
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
        menu.close();
    }

    HttpResponse<String> post(String body) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(base + "/orders")).POST(HttpRequest.BodyPublishers.ofString(body))
                .header("Content-Type", "application/json").build(), HttpResponse.BodyHandlers.ofString());
    }

    static String body(String key, int quantity) {
        return "{\"idempotencyKey\":\"" + key + "\",\"customerId\":\"c1\",\"restaurantId\":7,\"coupon\":\"FIRST20\",\"items\":[{\"itemId\":\"paneer-tikka\","
                + "\"quantity\":" + quantity + "}]}";
    }

    @Test
    void placingAnOrderAnswers201WithTheTotals() throws Exception {
        HttpResponse<String> r = post(body("k1", 2));
        assertEquals(201, r.statusCode());
        assertTrue(r.body().contains("\"total\":\"217.56\""), r.body());
        assertTrue(r.body().contains("\"discount\":\"51.80\""), r.body());
    }

    @Test
    void aRetryAnswers200WithTheSameOrder() throws Exception {
        String first = post(body("k1", 2)).body();
        HttpResponse<String> again = post(body("k1", 2));
        assertEquals(200, again.statusCode());
        assertEquals(first, again.body());
    }

    @Test
    void anInvalidQuantityIs400() throws Exception {
        assertEquals(400, post(body("k1", 25)).statusCode());
    }

    @Test
    void aStoredOrderCanBeRead() throws Exception {
        String created = post(body("k1", 2)).body();
        String id = created.replaceAll(".*\"id\":(\\d+).*", "$1");
        HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(base + "/orders/" + id)).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, r.statusCode());
        assertEquals(created, r.body());
        assertEquals(404, http.send(HttpRequest.newBuilder(URI.create(base + "/orders/999999")).build(), HttpResponse.BodyHandlers.ofString()).statusCode());
    }
}
