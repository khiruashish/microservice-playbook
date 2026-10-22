package dev.foodfleet.ch02.menu.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/** Design for failure, tested without Spring: every degradation path has an explicit, asserted outcome. */
class SurgePricingTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-01T13:00:00Z"));
    private final AtomicBoolean pricingUp = new AtomicBoolean(true);

    private final SurgeLookup flakyLookup = zone -> {
        if (!pricingUp.get()) {
            throw new IllegalStateException("pricing-service unreachable");
        }
        return 15_000;
    };

    private final SurgePricing pricing =
            new SurgePricing(flakyLookup, clock, new PricingProperties(Duration.ofMinutes(5)));

    @Test
    void liveWhenPricingServiceAnswers() {
        assertThat(pricing.quote("BLR-HSR"))
                .isEqualTo(new SurgeQuote("BLR-HSR", 15_000, PriceSource.LIVE));
    }

    @Test
    void lastKnownWhilePricingServiceIsDownAndValueIsFresh() {
        pricing.quote("BLR-HSR");
        pricingUp.set(false);
        clock.advance(Duration.ofMinutes(4));

        assertThat(pricing.quote("BLR-HSR"))
                .isEqualTo(new SurgeQuote("BLR-HSR", 15_000, PriceSource.LAST_KNOWN));
    }

    @Test
    void basePriceOnceLastKnownIsTooOld() {
        pricing.quote("BLR-HSR");
        pricingUp.set(false);
        clock.advance(Duration.ofMinutes(6));

        assertThat(pricing.quote("BLR-HSR"))
                .isEqualTo(new SurgeQuote("BLR-HSR", SurgeQuote.BASE_BPS, PriceSource.BASE));
    }

    @Test
    void basePriceWhenNothingWasEverKnown() {
        pricingUp.set(false);

        assertThat(pricing.quote("BLR-WHITEFIELD").source()).isEqualTo(PriceSource.BASE);
    }

    @Test
    void multiplierRoundsHalfUpToTheNearestPaisa() {
        assertThat(new SurgeQuote("z", 12_500, PriceSource.LIVE).apply(34_900)).isEqualTo(43_625);
        assertThat(new SurgeQuote("z", 15_000, PriceSource.LIVE).apply(10_001)).isEqualTo(15_002); // 15001.5 → 15002
        assertThat(new SurgeQuote("z", 12_500, PriceSource.LIVE).apply(12_345)).isEqualTo(15_431); // 15431.25 → 15431
    }
}
