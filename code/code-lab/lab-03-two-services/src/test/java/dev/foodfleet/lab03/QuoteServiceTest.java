package dev.foodfleet.lab03;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The real HTTP client against a stand-in menu-service on a random port: the URL, the JSON mapping, the status
 * handling and the read timeout are all exercised, which a mocked MenuClient would skip.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class QuoteServiceTest {

    static HttpServer menuService;

    @BeforeAll
    static void startMenuService() throws IOException {
        menuService = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        menuService.createContext("/restaurants/r-1/menu", exchange -> {
            byte[] body = """
                    {"restaurantId":"r-1","items":[
                      {"id":"m-1","name":"Paneer tikka","pricePaise":24900,"vegetarian":true,"spiceLevel":2},
                      {"id":"m-2","name":"Chicken biryani","pricePaise":32900,"vegetarian":false}]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        menuService.createContext("/restaurants/r-slow/menu", exchange -> {
            try {
                Thread.sleep(Duration.ofSeconds(3));                 // longer than the 1 s read timeout
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });
        menuService.createContext("/", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        menuService.start();
    }

    @AfterAll
    static void stop() {
        menuService.stop(0);
    }

    @DynamicPropertySource
    static void menuServiceUrl(DynamicPropertyRegistry registry) {
        registry.add("spring.http.serviceclient.menu.base-url",
                () -> "http://127.0.0.1:" + menuService.getAddress().getPort());
    }

    @Autowired
    QuoteService quotes;

    @Test
    void pricesTheOrderFromTheMenu() {
        var quote = quotes.quote("r-1", List.of(new QuoteService.Line("m-1", 2), new QuoteService.Line("m-2", 1)));
        assertThat(quote.totalPaise()).isEqualTo(2 * 24900 + 32900);
    }

    @Test
    void ignoresFieldsItDoesntKnow() {
        // "spiceLevel" and "vegetarian" aren't in MenuClient.Item: a tolerant reader doesn't fail on them
        assertThat(quotes.quote("r-1", List.of(new QuoteService.Line("m-1", 1))).lines()).hasSize(1);
    }

    @Test
    void unknownDishIsRefused() {
        assertThatThrownBy(() -> quotes.quote("r-1", List.of(new QuoteService.Line("m-99", 1))))
                .isInstanceOf(QuoteService.UnknownItems.class).hasMessageContaining("m-99");
    }

    @Test
    void menuServicesNotFoundBecomesOurs() {
        assertThatThrownBy(() -> quotes.quote("r-9", List.of(new QuoteService.Line("m-1", 1))))
                .isInstanceOf(QuoteService.RestaurantNotFound.class);
    }

    @Test
    void aSlowMenuServiceFailsFastAtTheReadTimeout() {
        long start = System.nanoTime();
        assertThatThrownBy(() -> quotes.quote("r-slow", List.of(new QuoteService.Line("m-1", 1))))
                .isInstanceOf(QuoteService.MenuUnavailable.class);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
    }
}
