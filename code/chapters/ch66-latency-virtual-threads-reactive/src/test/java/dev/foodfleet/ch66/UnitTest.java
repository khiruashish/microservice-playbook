package dev.foodfleet.ch66;

import com.sun.net.httpserver.HttpServer;
import dev.foodfleet.ch66.backend.Backend;
import dev.foodfleet.ch66.quote.QuoteServer;
import dev.foodfleet.ch66.tail.FanOut;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class UnitTest {

    /** Every concurrency model answers the same quote, after three sequential 20 ms calls. */
    @ParameterizedTest
    @ValueSource(strings = {"platform", "virtual", "async", "virtual-limited"})
    void everyModelServesTheQuote(String model) throws Exception {
        HttpServer backend = Backend.start(0, 20, 20, 0);
        HttpServer quote = QuoteServer.start(model, 0, "http://127.0.0.1:" + backend.getAddress().getPort());
        try {
            long t = System.nanoTime();
            HttpResponse<String> r = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + quote.getAddress().getPort() + "/quote")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, r.statusCode());
            assertTrue(r.body().contains("\"total\""));
            assertTrue((System.nanoTime() - t) / 1_000_000 >= 60, "three sequential calls of 20 ms");
        } finally {
            quote.stop(0);
            backend.stop(0);
        }
    }

    @Test
    void hedgingSendsASecondCopyOfASlowCall() throws Exception {
        HttpServer backend = Backend.start(0, 200, 200, 0);
        try {
            FanOut f = new FanOut("http://127.0.0.1:" + backend.getAddress().getPort());
            f.request(1, 20);
            assertEquals(1, f.hedges.get());
            assertEquals(2, f.calls.get());
        } finally {
            backend.stop(0);
        }
    }

    @Test
    void aHedgingBudgetLimitsTheCopies() throws Exception {
        HttpServer backend = Backend.start(0, 100, 100, 0);
        try {
            FanOut f = new FanOut("http://127.0.0.1:" + backend.getAddress().getPort(), 0.05);
            f.request(40, 20);
            assertTrue(f.hedges.get() <= 0.05 * f.calls.get() + 1, f.hedges + " of " + f.calls);
        } finally {
            backend.stop(0);
        }
    }
}
