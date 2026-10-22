package dev.foodfleet.ch16.rollout;

import dev.foodfleet.ch16.pricing.LegacyPricingAdapter;
import dev.foodfleet.ch16.pricing.NewPricing;
import dev.foodfleet.ch16.pricing.PriceRequest;
import dev.foodfleet.ch16.pricing.PricingService;
import dev.foodfleet.ch16.strangler.Routing;
import dev.foodfleet.ch16.strangler.ShadowComparator;
import dev.foodfleet.ch16.strangler.StranglerFacade;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Migrating pricing out of the monolith, in two experiments.
 *
 * <p><b>A. Shadow traffic.</b> 50,000 requests through the facade in SHADOW mode for each release of
 * pricing-service. Customers always get the monolith's price; we count how often the new one differs.
 *
 * <p><b>B. Rollout with an incident.</b> pricing-service V3 is rolled out while a zone-data migration
 * has just dropped Whitefield (3% of traffic): pricing-service fails those requests. 1,000 requests a
 * minute for an hour, three strategies, the same automated guard: every minute, if the new path served
 * at least 200 requests in the last 5 minutes and more than 1% failed, route everything back to the
 * monolith.
 */
public final class RolloutSimulation {

    public record ShadowResult(NewPricing.Release release, int requests, int match, int withinTolerance,
                               int mismatch, int failed, Map<String, Integer> mismatchesByField) {
    }

    public enum Strategy {
        BIG_BANG_NO_GUARD("big bang, no guard (on-call rolls back at 20 min)", List.of(100), false, false),
        BIG_BANG("big bang: 100% at once", List.of(100), false, true),
        CANARY("canary 1-5-25-50-100%, 10 min per step", List.of(1, 5, 25, 50, 100), false, true),
        CANARY_WITH_FALLBACK("canary + fall back to monolith on error", List.of(1, 5, 25, 50, 100), true, true);

        public final String label;
        final List<Integer> steps;
        final boolean fallback;
        final boolean guarded;

        Strategy(String label, List<Integer> steps, boolean fallback, boolean guarded) {
            this.label = label;
            this.steps = steps;
            this.fallback = fallback;
            this.guarded = guarded;
        }
    }

    public record RolloutResult(Strategy strategy, int rolledBackAtMinute, int newPathRequests,
                                int customerErrors, int customersAffected, int fallbacks) {
    }

    public static ShadowResult shadow(NewPricing.Release release, int requests, long seed) {
        ShadowComparator comparator = new ShadowComparator(1);
        PricingService facade = new StranglerFacade(new LegacyPricingAdapter(),
                new NewPricing(release, Traffic.allZones()), comparator, Routing::shadow, StranglerFacade.Listener.NONE);
        Traffic traffic = new Traffic(seed);
        for (int i = 0; i < requests; i++) {
            facade.quote(traffic.next());
        }
        return new ShadowResult(release, comparator.total(), comparator.count(ShadowComparator.Outcome.MATCH),
                comparator.count(ShadowComparator.Outcome.WITHIN_TOLERANCE),
                comparator.count(ShadowComparator.Outcome.MISMATCH),
                comparator.count(ShadowComparator.Outcome.NEW_FAILED), comparator.mismatchesByField());
    }

    public static RolloutResult rollout(Strategy strategy, long seed) {
        Set<String> zonesAfterBadMigration = new HashSet<>(Traffic.allZones());
        zonesAfterBadMigration.remove("WHITEFIELD");
        AtomicReference<Routing> routing = new AtomicReference<>(route(strategy, strategy.steps.getFirst()));

        int[] minuteNew = new int[60];
        int[] minuteFailed = new int[60];
        int[] minute = {0};
        int[] fallbacks = {0};
        StranglerFacade.Listener listener = (request, served, failed) -> {
            if (served != StranglerFacade.Served.LEGACY) {
                minuteNew[minute[0]]++;
                if (failed) {
                    minuteFailed[minute[0]]++;
                }
                if (served == StranglerFacade.Served.FALLBACK) {
                    fallbacks[0]++;
                }
            }
        };
        PricingService facade = new StranglerFacade(new LegacyPricingAdapter(),
                new NewPricing(NewPricing.Release.V3, zonesAfterBadMigration), new ShadowComparator(1),
                routing::get, listener);

        Traffic traffic = new Traffic(seed);
        Set<String> affected = new HashSet<>();
        int errors = 0;
        int rolledBackAt = -1;
        Deque<Integer> steps = new ArrayDeque<>(strategy.steps);
        steps.removeFirst();
        for (; minute[0] < 60; minute[0]++) {
            for (int r = 0; r < 1_000; r++) {
                PriceRequest request = traffic.next();
                try {
                    facade.quote(request);
                } catch (NewPricing.UnknownZone e) {
                    errors++;
                    affected.add(request.customerId());      // this customer saw "couldn't price your order"
                }
            }
            boolean manualRollback = !strategy.guarded && minute[0] + 1 == 20;
            if (rolledBackAt < 0 && (manualRollback
                    || strategy.guarded && guardTrips(minuteNew, minuteFailed, minute[0]))) {
                routing.set(Routing.legacy());
                rolledBackAt = minute[0] + 1;
            } else if (rolledBackAt < 0 && (minute[0] + 1) % 10 == 0 && !steps.isEmpty()) {
                routing.set(route(strategy, steps.removeFirst()));
            }
        }
        int served = 0;
        for (int n : minuteNew) {
            served += n;
        }
        return new RolloutResult(strategy, rolledBackAt, served, errors, affected.size(), fallbacks[0]);
    }

    private static Routing route(Strategy strategy, int percent) {
        Routing routing = percent >= 100 ? Routing.newOnly() : Routing.canary(percent);
        return strategy.fallback ? routing.withFallback() : routing;
    }

    /** At least 200 new-path requests in the last 5 minutes and more than 1% of them failed. */
    static boolean guardTrips(int[] served, int[] failed, int now) {
        int n = 0;
        int f = 0;
        for (int m = Math.max(0, now - 4); m <= now; m++) {
            n += served[m];
            f += failed[m];
        }
        return n >= 200 && f * 100 > n;
    }

    public static void main(String[] args) {
        System.out.println("A. Shadow traffic: 50,000 requests per release; customers always get the monolith's price\n");
        System.out.printf("%-8s %8s %10s %10s %9s  %s%n", "release", "match", "<=1 paise", "mismatch", "failed",
                "mismatches by differing field");
        for (NewPricing.Release release : NewPricing.Release.values()) {
            ShadowResult r = shadow(release, 50_000, 16);
            System.out.printf("%-8s %8d %10d %10d %9d  %s%n", release, r.match(), r.withinTolerance(), r.mismatch(),
                    r.failed(), r.mismatchesByField());
        }
        System.out.println("\nB. Rolling out V3 while Whitefield (3% of traffic) is missing from its zone data");
        System.out.println("   1,000 requests/minute for 60 minutes; guard: >1% failures over 5 min with >=200 requests\n");
        System.out.printf("%-50s %11s %9s %13s %10s %9s%n", "strategy", "rolled back", "new-path", "errors shown",
                "customers", "fallbacks");
        for (Strategy strategy : Strategy.values()) {
            RolloutResult r = rollout(strategy, 16);
            System.out.printf("%-50s %11s %9d %13d %10d %9d%n", strategy.label,
                    r.rolledBackAtMinute() < 0 ? "never" : "minute " + r.rolledBackAtMinute(), r.newPathRequests(),
                    r.customerErrors(), r.customersAffected(), r.fallbacks());
        }
    }
}
