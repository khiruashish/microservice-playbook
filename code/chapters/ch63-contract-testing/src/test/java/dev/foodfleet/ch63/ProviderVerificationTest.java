package dev.foodfleet.ch63;

import com.sun.net.httpserver.HttpServer;
import dev.foodfleet.ch63.contract.Contract;
import dev.foodfleet.ch63.contract.Verifier;
import dev.foodfleet.ch63.menu.MenuService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** menu-service's side: every consumer's contract verified against the real provider, in the provider's build. */
class ProviderVerificationTest {

    static HttpServer server;

    @BeforeAll
    static void start() throws Exception {
        server = MenuService.start(0);
    }

    @AfterAll
    static void stop() {
        server.stop(0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"order-service-menu-service.json", "app-bff-menu-service.json"})
    void honoursEveryConsumersContract(String file) {
        List<Verifier.Result> results = Verifier.verify(Contract.load(Path.of("src/test/resources/contracts", file)),
                "http://127.0.0.1:" + server.getAddress().getPort());
        assertTrue(results.stream().allMatch(Verifier.Result::ok), results.toString());
    }
}
