package dev.foodfleet.lab04;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** The gateway in front of two stand-in menu-service instances: routing, prefix stripping, ids and balancing. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RoutesTest {

    static final List<HttpServer> instances = new ArrayList<>();
    static final List<String> seen = Collections.synchronizedList(new ArrayList<>());

    @BeforeAll
    static void startInstances() throws IOException {
        for (String name : List.of("menu-a", "menu-b")) {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                seen.add(name + " " + exchange.getRequestURI().getPath() + " id=" + exchange.getRequestHeaders().getFirst("X-Request-Id"));
                byte[] body = ("{\"servedBy\":\"" + name + "\"}").getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            instances.add(server);
        }
    }

    @AfterAll
    static void stop() {
        instances.forEach(s -> s.stop(0));
    }

    @DynamicPropertySource
    static void instances(DynamicPropertyRegistry registry) {
        for (int i = 0; i < instances.size(); i++) {
            int port = instances.get(i).getAddress().getPort();
            registry.add("spring.cloud.discovery.client.simple.instances.menu-service[" + i + "].uri", () -> "http://127.0.0.1:" + port);
        }
    }

    @LocalServerPort
    int port;

    @Test
    void routesStripsThePrefixAddsAnIdAndBalances() throws Exception {
        HttpClient http = HttpClient.newHttpClient();
        for (int i = 0; i < 4; i++) {
            HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/restaurants/r-1/menu")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(r.statusCode()).isEqualTo(200);
        }
        assertThat(seen).hasSize(4).allSatisfy(line -> assertThat(line).contains(" /restaurants/r-1/menu id="));
        assertThat(seen).noneMatch(line -> line.endsWith("id=null"));
        assertThat(seen.stream().filter(l -> l.startsWith("menu-a")).count()).isEqualTo(2);   // round robin: 2 and 2
        assertThat(seen.stream().filter(l -> l.startsWith("menu-b")).count()).isEqualTo(2);
    }

    @Test
    void unknownPathsAreNotRouted() throws Exception {
        HttpResponse<String> r = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/internal/admin")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(r.statusCode()).isEqualTo(404);
    }
}
