package dev.foodfleet.ch16.strangler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.foodfleet.ch16.pricing.PriceQuote;
import dev.foodfleet.ch16.pricing.PriceRequest;
import dev.foodfleet.ch16.pricing.PricingService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class StranglerFacadeTest {

    private static final PriceQuote LEGACY = new PriceQuote(10_000, 2_500, 0, 12_500);
    private static final PriceQuote NEW = new PriceQuote(10_000, 2_500, 0, 12_600);

    private final AtomicReference<Routing> routing = new AtomicReference<>(Routing.legacy());
    private final ShadowComparator comparator = new ShadowComparator(1);
    private final List<StranglerFacade.Served> served = new ArrayList<>();
    private PricingService candidate = request -> NEW;
    private final StranglerFacade facade = new StranglerFacade(request -> LEGACY, request -> candidate.quote(request),
            comparator, routing::get, (request, how, failed) -> served.add(how));

    private static PriceRequest from(String customer) {
        return new PriceRequest(customer, PriceRequest.Tier.NONE, List.of(10_000L), 1_000,
                Instant.parse("2026-10-06T06:30:00Z"), "HSR");
    }

    @Test
    void legacyModeNeverCallsTheNewService() {
        candidate = request -> {
            throw new AssertionError("must not be called");
        };

        assertThat(facade.quote(from("c-1"))).isEqualTo(LEGACY);
    }

    @Test
    void shadowModeAnswersFromTheMonolithAndRecordsTheDifference() {
        routing.set(Routing.shadow());

        assertThat(facade.quote(from("c-1"))).isEqualTo(LEGACY);
        assertThat(comparator.count(ShadowComparator.Outcome.MISMATCH)).isEqualTo(1);
    }

    @Test
    void aFailingShadowCallNeverReachesTheCustomer() {
        routing.set(Routing.shadow());
        candidate = request -> {
            throw new IllegalStateException("pricing-service down");
        };

        assertThat(facade.quote(from("c-1"))).isEqualTo(LEGACY);
        assertThat(comparator.count(ShadowComparator.Outcome.NEW_FAILED)).isEqualTo(1);
    }

    @Test
    void canaryRoutesByCustomerCohort() {
        routing.set(Routing.canary(10));
        String inside = firstCustomer(true);
        String outside = firstCustomer(false);

        assertThat(facade.quote(from(inside))).isEqualTo(NEW);
        assertThat(facade.quote(from(outside))).isEqualTo(LEGACY);
        assertThat(facade.quote(from(inside))).isEqualTo(NEW);            // sticky
    }

    @Test
    void withFallbackAFailureIsAnsweredByTheMonolith() {
        routing.set(Routing.newOnly().withFallback());
        candidate = request -> {
            throw new IllegalStateException("pricing-service down");
        };

        assertThat(facade.quote(from("c-1"))).isEqualTo(LEGACY);
        assertThat(served).containsExactly(StranglerFacade.Served.FALLBACK);
    }

    @Test
    void withoutFallbackTheFailureIsTheCustomers() {
        routing.set(Routing.newOnly());
        candidate = request -> {
            throw new IllegalStateException("pricing-service down");
        };

        assertThatThrownBy(() -> facade.quote(from("c-1"))).isInstanceOf(IllegalStateException.class);
    }

    private static String firstCustomer(boolean inCanary) {
        for (int i = 0; ; i++) {
            if (Cohorts.inRollout("c-" + i, 10) == inCanary) {
                return "c-" + i;
            }
        }
    }
}
