package dev.foodfleet.ch65;

import dev.foodfleet.ch65.arch.Rules;
import dev.foodfleet.ch65.chaos.FaultProxy;
import dev.foodfleet.ch65.chaos.Services;
import dev.foodfleet.ch65.load.Latencies;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("unit")
class UnitTest {

    @Test
    void percentilesAreNearestRank() {
        Latencies l = new Latencies();
        for (int i = 1; i <= 100; i++) {
            l.record(i * 1000L);
        }
        assertEquals(50.0, l.percentile(50));
        assertEquals(99.0, l.percentile(99));
        assertEquals(100.0, l.max());
    }

    @Test
    void correctionAddsTheRequestsThatWouldHaveWaited() {
        Latencies l = new Latencies();
        l.recordCorrected(1_000_000, 100_000); // 1 s, when one was due every 100 ms
        assertEquals(10, l.count());
        assertEquals(100.0, l.percentile(0.1));
    }

    @Test
    void cyclesAreFoundOnce() {
        assertEquals(List.of("cycle: a -> b -> a"), Rules.cycles(Map.of("a", Set.of("b"), "b", Set.of("a"))));
    }

    @Test
    void theProxyInjectsErrors() throws Exception {
        var menu = Services.menu();
        try (FaultProxy proxy = new FaultProxy("http://127.0.0.1:" + menu.getAddress().getPort())) {
            HttpClient http = HttpClient.newHttpClient();
            HttpRequest req = HttpRequest.newBuilder(URI.create(proxy.url() + "/restaurants/7/items/x")).build();
            assertEquals(200, http.send(req, HttpResponse.BodyHandlers.discarding()).statusCode());
            proxy.inject(new FaultProxy.Errors(1.0));
            assertEquals(503, http.send(req, HttpResponse.BodyHandlers.discarding()).statusCode());
        } finally {
            menu.stop(0);
        }
    }
}
