package dev.foodfleet.lab06;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The real client, aspects and policies against a stand-in menu-service whose behaviour each test sets: healthy,
 * failing with 503, failing once, or answering 404. The stand-in counts the calls that reach it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ResilientMenusTest {

    enum Mode { OK, DOWN, FAIL_ONCE, NOT_FOUND }

    static HttpServer menuService;
    static final AtomicReference<Mode> mode = new AtomicReference<>(Mode.OK);
    static final AtomicInteger calls = new AtomicInteger();

    @BeforeAll
    static void start() throws IOException {
        menuService = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        menuService.createContext("/", exchange -> {
            int n = calls.incrementAndGet();
            Mode m = mode.get();
            int status = switch (m) {
                case OK -> 200;
                case DOWN -> 503;
                case FAIL_ONCE -> n == 1 ? 503 : 200;
                case NOT_FOUND -> 404;
            };
            byte[] body = status == 200
                    ? "{\"restaurantId\":\"r-1\",\"items\":[{\"id\":\"m-1\",\"name\":\"Paneer tikka\",\"pricePaise\":24900}]}"
                            .getBytes(StandardCharsets.UTF_8)
                    : new byte[0];
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
            if (body.length > 0) {
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        menuService.start();
    }

    @AfterAll
    static void stop() {
        menuService.stop(0);
    }

    @DynamicPropertySource
    static void url(DynamicPropertyRegistry registry) {
        registry.add("spring.http.serviceclient.menu.base-url", () -> "http://127.0.0.1:" + menuService.getAddress().getPort());
    }

    @Autowired
    ResilientMenus menus;

    @Autowired
    CircuitBreakerRegistry breakers;

    @BeforeEach
    void reset() {
        breakers.circuitBreaker("menu").reset();
        mode.set(Mode.OK);
        calls.set(0);
    }

    @Test
    void aTransientFailureIsRetriedOnce() {
        mode.set(Mode.FAIL_ONCE);
        assertThat(menus.menu("r-1").stale()).isFalse();
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void aDownServiceFallsBackToTheLastKnownMenu() {
        menus.menu("r-1");                                // warm the copy
        mode.set(Mode.DOWN);
        calls.set(0);
        assertThat(menus.menu("r-1").stale()).isTrue();
        assertThat(calls.get()).isEqualTo(3);             // three attempts, then the fallback
    }

    @Test
    void notFoundIsNeitherRetriedNorCountedAsAFailure() {
        mode.set(Mode.NOT_FOUND);
        assertThatThrownBy(() -> menus.menu("r-9")).isInstanceOf(ResilientMenus.MenuUnavailable.class);
        assertThat(calls.get()).isEqualTo(1);
        assertThat(breakers.circuitBreaker("menu").getMetrics().getNumberOfFailedCalls()).isZero();
    }

    @Test
    void theBreakerOpensAndStopsCallingADeadService() {
        menus.menu("r-1");
        mode.set(Mode.DOWN);
        for (int i = 0; i < 10; i++) {                    // 10 requests x 3 attempts: past the 20-call minimum
            menus.menu("r-1");
        }
        assertThat(breakers.circuitBreaker("menu").getState().name()).isEqualTo("OPEN");
        int before = calls.get();
        for (int i = 0; i < 50; i++) {
            assertThat(menus.menu("r-1").stale()).isTrue();   // still answered, from the copy
        }
        assertThat(calls.get()).isEqualTo(before);            // and menu-service got none of these 50
    }
}
