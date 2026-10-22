package dev.foodfleet.ch16.branch;

import static org.assertj.core.api.Assertions.assertThat;

import dev.foodfleet.ch16.pricing.LegacyPricingAdapter;
import dev.foodfleet.ch16.pricing.NewPricing;
import dev.foodfleet.ch16.pricing.PriceRequest;
import dev.foodfleet.ch16.strangler.Routing;
import dev.foodfleet.ch16.strangler.ShadowComparator;
import dev.foodfleet.ch16.strangler.StranglerFacade;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class CheckoutServiceTest {

    @Test
    void checkoutDoesNotKnowWhichImplementationAnswered() {
        AtomicReference<Routing> routing = new AtomicReference<>(Routing.legacy());
        CheckoutService checkout = new CheckoutService(new StranglerFacade(new LegacyPricingAdapter(),
                new NewPricing(NewPricing.Release.V3, Set.of("HSR")), new ShadowComparator(1), routing::get,
                StranglerFacade.Listener.NONE));
        PriceRequest request = new PriceRequest("c-1", PriceRequest.Tier.SILVER, List.of(54_950L), 4_000,
                Instant.parse("2026-10-06T06:30:00Z"), "HSR");

        CheckoutService.Receipt before = checkout.checkout(request);
        routing.set(Routing.newOnly());                            // the switch: a config change
        CheckoutService.Receipt after = checkout.checkout(request);

        assertThat(after).isEqualTo(before);
        assertThat(after.quote().deliveryFeePaise()).isZero();      // silver, above Rs 499
    }
}
