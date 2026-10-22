package dev.foodfleet.ch16.strangler;

import dev.foodfleet.ch16.pricing.PriceQuote;
import dev.foodfleet.ch16.pricing.PriceRequest;
import dev.foodfleet.ch16.pricing.PricingService;
import java.util.function.Supplier;

/**
 * The strangler facade for the pricing capability: every caller goes through it, and it decides,
 * per request, whether the monolith or pricing-service answers. Routing is changed at runtime (a
 * flag or config), so moving forward and rolling back are config changes, not deployments.
 */
public final class StranglerFacade implements PricingService {

    public enum Served { LEGACY, NEW, FALLBACK }

    public interface Listener {
        Listener NONE = (request, served, failed) -> { };

        void served(PriceRequest request, Served served, boolean newPathFailed);
    }

    private final PricingService legacy;
    private final PricingService candidate;
    private final ShadowComparator comparator;
    private final Supplier<Routing> routing;
    private final Listener listener;

    public StranglerFacade(PricingService legacy, PricingService candidate, ShadowComparator comparator,
                           Supplier<Routing> routing, Listener listener) {
        this.legacy = legacy;
        this.candidate = candidate;
        this.comparator = comparator;
        this.routing = routing;
        this.listener = listener;
    }

    @Override
    public PriceQuote quote(PriceRequest request) {
        Routing current = routing.get();
        return switch (current.mode()) {
            case LEGACY -> served(request, Served.LEGACY, false, legacy.quote(request));
            case SHADOW -> shadow(request);
            case CANARY -> Cohorts.inRollout(request.customerId(), current.percent())
                    ? viaNew(request, current) : served(request, Served.LEGACY, false, legacy.quote(request));
            case NEW -> viaNew(request, current);
        };
    }

    /** The customer gets the monolith's answer; the new one is only compared. In production the
     *  shadow call runs asynchronously, with a timeout, and never on non-idempotent writes. */
    private PriceQuote shadow(PriceRequest request) {
        PriceQuote answer = legacy.quote(request);
        try {
            comparator.compare(request, answer, candidate.quote(request));
        } catch (RuntimeException e) {
            comparator.newServiceFailed();
        }
        return served(request, Served.LEGACY, false, answer);
    }

    private PriceQuote viaNew(PriceRequest request, Routing current) {
        try {
            return served(request, Served.NEW, false, candidate.quote(request));
        } catch (RuntimeException e) {
            if (current.fallbackToLegacy()) {
                return served(request, Served.FALLBACK, true, legacy.quote(request));
            }
            listener.served(request, Served.NEW, true);
            throw e;
        }
    }

    private PriceQuote served(PriceRequest request, Served served, boolean failed, PriceQuote quote) {
        listener.served(request, served, failed);
        return quote;
    }
}
