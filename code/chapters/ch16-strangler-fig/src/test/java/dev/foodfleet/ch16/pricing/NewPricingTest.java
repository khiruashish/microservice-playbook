package dev.foodfleet.ch16.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.foodfleet.ch16.pricing.NewPricing.Release;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class NewPricingTest {

    private static final Instant ELEVEN_PM_IST = Instant.parse("2026-10-06T17:30:00Z");
    private static final Instant NOON_IST = Instant.parse("2026-10-06T06:30:00Z");
    private static final Set<String> ZONES = Set.of("HSR");

    private final PricingService legacy = new LegacyPricingAdapter();

    private static PriceRequest request(PriceRequest.Tier tier, long itemPaise, int meters, Instant at) {
        return new PriceRequest("c-1", tier, List.of(itemPaise), meters, at, "HSR");
    }

    @Test
    void releaseThreeMatchesTheMonolithOnOrdinaryOrders() {
        PriceRequest request = request(PriceRequest.Tier.NONE, 29_950, 5_200, NOON_IST);

        assertThat(new NewPricing(Release.V3, ZONES).quote(request)).isEqualTo(legacy.quote(request));
        assertThat(legacy.quote(request)).isEqualTo(new PriceQuote(29_950, 4_900, 0, 34_850));   // 25 + 3 km x 8
    }

    @Test
    void releaseOneJudgedLateNightInUtc() {
        PriceRequest request = request(PriceRequest.Tier.NONE, 29_950, 2_000, ELEVEN_PM_IST);   // 17:30 UTC

        assertThat(new NewPricing(Release.V1, ZONES).quote(request).surchargePaise()).isZero();
        assertThat(legacy.quote(request).surchargePaise()).isEqualTo(4_868L);
    }

    @Test
    void releaseOneForgotGoldFreeDelivery() {
        PriceRequest request = request(PriceRequest.Tier.GOLD, 29_950, 2_000, NOON_IST);

        assertThat(new NewPricing(Release.V1, ZONES).quote(request).deliveryFeePaise()).isEqualTo(2_500L);
        assertThat(new NewPricing(Release.V2, ZONES).quote(request).deliveryFeePaise()).isZero();
    }

    @Test
    void theMonolithDoesNotRoundHalfUpConsistently() {
        // Gold, one item at Rs 224.50, 11 pm: 15% of 224.50 is 33.675. In double arithmetic
        // 224.5 * 0.15 * 100 = 3367.4999999999995, so the monolith charges 33.67.
        PriceRequest request = request(PriceRequest.Tier.GOLD, 22_450, 2_000, ELEVEN_PM_IST);

        assertThat(legacy.quote(request).surchargePaise()).isEqualTo(3_367L);
        assertThat(new NewPricing(Release.V3, ZONES).quote(request).surchargePaise()).isEqualTo(3_368L);
        assertThat(new NewPricing(Release.V2, ZONES).quote(request).surchargePaise()).isEqualTo(3_367L);  // floor
    }

    @Test
    void unknownZonesAreRefused() {
        PriceRequest request = new PriceRequest("c-1", PriceRequest.Tier.NONE, List.of(9_900L), 1_000, NOON_IST,
                "WHITEFIELD");

        assertThatThrownBy(() -> new NewPricing(Release.V3, ZONES).quote(request))
                .isInstanceOf(NewPricing.UnknownZone.class);
    }
}
