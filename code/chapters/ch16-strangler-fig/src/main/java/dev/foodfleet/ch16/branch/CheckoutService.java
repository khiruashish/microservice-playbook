package dev.foodfleet.ch16.branch;

import dev.foodfleet.ch16.pricing.PriceQuote;
import dev.foodfleet.ch16.pricing.PriceRequest;
import dev.foodfleet.ch16.pricing.PricingService;

/**
 * Checkout inside the monolith, after branch by abstraction.
 *
 * <p>Pricing isn't only reached through the edge: checkout, the cart page, re-orders and refunds call
 * it in-process, so routing at the gateway can't move those calls. Branch by abstraction does it
 * inside the codebase, on trunk, in small safe steps:
 * <ol>
 *   <li>introduce {@link PricingService} and make every caller use it, with the legacy code behind it
 *       ({@code LegacyPricingAdapter}); behaviour unchanged, merged and released;</li>
 *   <li>add the new implementation behind the same interface (a client for pricing-service);</li>
 *   <li>switch per cohort with a toggle ({@code StranglerFacade} does this: shadow, canary, new);</li>
 *   <li>when the new implementation has taken all traffic for long enough, delete the legacy code
 *       and, if it only ever had one implementation left, the toggle.</li>
 * </ol>
 * {@code LegacyIsolationTest} keeps step 1 true: only the adapter may call the legacy pricing code.
 */
public final class CheckoutService {

    public record Receipt(String customerId, PriceQuote quote) {
    }

    private final PricingService pricing;

    public CheckoutService(PricingService pricing) {
        this.pricing = pricing;
    }

    public Receipt checkout(PriceRequest request) {
        return new Receipt(request.customerId(), pricing.quote(request));
    }
}
