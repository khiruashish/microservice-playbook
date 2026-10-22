package dev.foodfleet.ch76;

import dev.foodfleet.ch76.functions.FeeQuote;
import dev.foodfleet.ch76.platform.Prices;
import dev.foodfleet.ch76.platform.Simulation;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class UnitTest {

    static final Simulation.Measured FIXED = new Simulation.Measured(new double[] {200}, new double[] {150}, new double[] {1.2});

    @Test
    void theFeeQuoteChargesDistanceAndSurgeAndWaivesBigBaskets() {
        FeeQuote f = new FeeQuote();
        assertEquals("{\"feeInPaise\":7860}", f.apply("{\"km\":6.5,\"basket\":32000,\"surge\":1.2}"));
        assertEquals("{\"feeInPaise\":0}", f.apply("{\"km\":1,\"basket\":50000}"));
    }

    @Test
    void functionsColdStartAfterTheKeepAliveAndBillPerMillisecond() {
        Simulation.Result r = Simulation.faas(new double[] {0, 1, 2, 400}, FIXED, 300, 1);
        assertEquals(2, r.cold(), "the first request, and the one after 398 s idle");
        double expected = 2 * Prices.lambda(200) + 2 * Prices.lambda(1.2);
        assertEquals(expected, r.cost(), 1e-15);
        assertEquals(Prices.lambda(2), Prices.lambda(1.2), "1.2 ms is billed as 2 ms");
        assertEquals(3, Simulation.faas(new double[] {0, 0.0001, 0.0002}, FIXED, 300, 1).cold(), "concurrent requests need their own instances");
    }

    @Test
    void scaleToZeroWaitsForAColdStartAndBillsAMinuteAtLeast() {
        Simulation.Result r = Simulation.scaleToZero(new double[] {0, 10, 200}, FIXED, 1);
        assertEquals(2, r.cold(), "zero instances 90 s after the last request, so the request at 200 s starts one");
        assertTrue(r.instanceHours() * 3_600 >= 2 * 60, "two instance starts, a minute each at least");
        assertEquals(200, r.max(), 1e-9);
    }
}
