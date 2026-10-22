package dev.foodfleet.ch18.simulation;

import dev.foodfleet.ch18.resilience.Bulkhead;
import dev.foodfleet.ch18.resilience.Call;
import dev.foodfleet.ch18.resilience.CircuitBreaker;
import dev.foodfleet.ch18.resilience.Fallback;
import dev.foodfleet.ch18.resilience.Outcome;
import dev.foodfleet.ch18.resilience.Retry;
import dev.foodfleet.ch18.resilience.Timeout;
import dev.foodfleet.ch18.sim.EventLoop;
import dev.foodfleet.ch18.sim.MapsProvider;
import dev.foodfleet.ch18.sim.TrackingService;
import dev.foodfleet.ch18.sim.TrackingService.Endpoint;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Five minutes of FoodFleet order tracking: 200 status requests and 100 ETA requests a second to a
 * tracking-service with 50 workers. From minute 1 to minute 3 the maps provider behind ETA browns
 * out: 80% of its calls take ~2.5 s instead of ~40 ms. Each scenario adds one resilience pattern; the traffic and the provider's
 * behaviour are the same seeded random streams in every run.
 */
public final class ResilienceSimulation {

    static final long INCIDENT_START = EventLoop.seconds(60);
    static final long INCIDENT_END = EventLoop.seconds(180);
    static final long DURATION = EventLoop.seconds(300);

    public enum Scenario {
        NOTHING("no protection"),
        TIMEOUT("timeout 300 ms"),
        TIMEOUT_RETRY("timeout + 3 attempts, no backoff"),
        TIMEOUT_BULKHEAD("timeout + bulkhead (20)"),
        TIMEOUT_BULKHEAD_BREAKER("timeout + bulkhead + circuit breaker"),
        ALL("+ fallback, 2 attempts w/ jittered backoff");

        public final String label;

        Scenario(String label) {
            this.label = label;
        }
    }

    public record Result(Scenario scenario, double statusOk, double statusP99Ms, double etaOk, double etaDegraded,
                         double etaFailed, double providerCallsPerEta, int providerPeakInFlight,
                         int recoverySeconds, long shortCircuited) {
    }

    /** Per-second counters by arrival time. */
    private static final class Buckets {
        final int[] statusTotal = new int[300];
        final int[] statusOk = new int[300];
        final int[] etaTotal = new int[300];
        final int[] etaOk = new int[300];
        final int[] etaDegraded = new int[300];
        final List<Long> incidentStatusLatency = new ArrayList<>();

        void record(TrackingService.Request request, Outcome outcome, long finishedAt) {
            int second = (int) (request.arrivedAt() / 1_000_000);
            boolean incident = request.arrivedAt() >= INCIDENT_START && request.arrivedAt() < INCIDENT_END;
            if (request.endpoint() == Endpoint.STATUS) {
                statusTotal[second]++;
                if (outcome == Outcome.SUCCESS) {
                    statusOk[second]++;
                }
                if (incident) {
                    incidentStatusLatency.add(finishedAt - request.arrivedAt());
                }
            } else {
                etaTotal[second]++;
                if (outcome == Outcome.SUCCESS) {
                    etaOk[second]++;
                } else if (outcome == Outcome.FALLBACK) {
                    etaDegraded[second]++;
                }
            }
        }
    }

    public static Result run(Scenario scenario, long seed) {
        EventLoop loop = new EventLoop();
        Random providerRandom = new Random(seed);
        Random retryRandom = new Random(seed + 1);
        Random arrivals = new Random(seed + 2);
        MapsProvider maps = new MapsProvider(loop, providerRandom, INCIDENT_START, INCIDENT_END);

        // The full stack, outermost first: fallback, bulkhead (one permit for the whole operation,
        // retries included), retry, circuit breaker (records every attempt), timeout.
        CircuitBreaker[] breaker = {null};
        Call eta = switch (scenario) {
            case NOTHING -> maps;
            case TIMEOUT -> timeout(loop, maps);
            case TIMEOUT_RETRY -> new Retry(loop, timeout(loop, maps), 3, 0, retryRandom);
            case TIMEOUT_BULKHEAD -> new Bulkhead(timeout(loop, maps), 20);
            case TIMEOUT_BULKHEAD_BREAKER -> new Bulkhead(breaker[0] = breaker(loop, timeout(loop, maps)), 20);
            case ALL -> new Fallback(new Bulkhead(new Retry(loop,
                    breaker[0] = breaker(loop, timeout(loop, maps)), 2, EventLoop.ms(100), retryRandom), 20));
        };

        Buckets buckets = new Buckets();
        TrackingService tracking = new TrackingService(loop, eta, buckets::record, 50, 200);
        schedule(loop, arrivals, 200, () -> tracking.arrive(Endpoint.STATUS));
        schedule(loop, arrivals, 100, () -> tracking.arrive(Endpoint.ETA));
        loop.runUntil(DURATION + TrackingService.CLIENT_TIMEOUT * 10);

        return summarise(scenario, buckets, maps, breaker[0]);
    }

    static Call timeout(EventLoop loop, Call call) {
        return new Timeout(loop, call, EventLoop.ms(300));
    }

    static CircuitBreaker breaker(EventLoop loop, Call call) {
        return new CircuitBreaker(loop, call, 50, 20, 50, EventLoop.seconds(5), 3);
    }

    /** Poisson arrivals at {@code perSecond}, for the whole run. */
    private static void schedule(EventLoop loop, Random random, double perSecond, Runnable arrival) {
        long[] next = {0};
        Runnable[] tick = new Runnable[1];
        tick[0] = () -> {
            arrival.run();
            next[0] += (long) (-Math.log(1 - random.nextDouble()) / perSecond * 1_000_000);
            if (next[0] < DURATION) {
                loop.at(next[0], tick[0]);
            }
        };
        loop.at(0, tick[0]);
    }

    private static Result summarise(Scenario scenario, Buckets b, MapsProvider maps, CircuitBreaker breaker) {
        int from = (int) (INCIDENT_START / 1_000_000);
        int to = (int) (INCIDENT_END / 1_000_000);
        long statusTotal = 0;
        long statusOk = 0;
        long etaTotal = 0;
        long etaOk = 0;
        long etaDegraded = 0;
        for (int s = from; s < to; s++) {
            statusTotal += b.statusTotal[s];
            statusOk += b.statusOk[s];
            etaTotal += b.etaTotal[s];
            etaOk += b.etaOk[s];
            etaDegraded += b.etaDegraded[s];
        }
        List<Long> latencies = new ArrayList<>(b.incidentStatusLatency);
        Collections.sort(latencies);
        double p99 = latencies.isEmpty() ? 0 : latencies.get((int) Math.ceil(0.99 * latencies.size()) - 1) / 1_000.0;
        return new Result(scenario, pct(statusOk, statusTotal), p99, pct(etaOk, etaTotal), pct(etaDegraded, etaTotal),
                100 - pct(etaOk, etaTotal) - pct(etaDegraded, etaTotal),
                (double) maps.callsDuringIncident() / etaTotal, maps.peakInFlight(), recovery(b, to),
                breaker == null ? 0 : breaker.shortCircuited());
    }

    /** Seconds after the incident ends until both endpoints stay >= 99% fresh successes for 10 s. */
    static int recovery(Buckets b, int incidentEnd) {
        for (int s = incidentEnd; s + 10 <= 300; s++) {
            boolean healthy = true;
            for (int t = s; t < s + 10 && healthy; t++) {
                healthy = b.statusOk[t] >= 0.99 * b.statusTotal[t] && b.etaOk[t] >= 0.99 * b.etaTotal[t];
            }
            if (healthy) {
                return s - incidentEnd;
            }
        }
        return -1;
    }

    private static double pct(long part, long total) {
        return total == 0 ? 0 : 100.0 * part / total;
    }

    public static void main(String[] args) {
        System.out.println("tracking-service, 50 workers; 200 status + 100 ETA req/s; maps provider browns out");
        System.out.println("(80% of calls ~2.5 s) from t=60s to t=180s. Figures for requests arriving during the incident.\n");
        System.out.printf("%-44s %9s %9s %8s %9s %8s %10s %6s %9s%n", "scenario", "status ok", "status", "ETA ok",
                "ETA", "ETA", "maps calls", "maps", "recovery");
        System.out.printf("%-44s %9s %9s %8s %9s %8s %10s %6s %9s%n", "", "", "p99 ms", "", "degraded", "failed",
                "per ETA", "peak", "after");
        for (Scenario scenario : Scenario.values()) {
            Result r = run(scenario, 18);
            System.out.printf("%-44s %8.1f%% %9.0f %7.1f%% %8.1f%% %7.1f%% %10.2f %6d %9s%n", scenario.label,
                    r.statusOk(), r.statusP99Ms(), r.etaOk(), r.etaDegraded(), r.etaFailed(), r.providerCallsPerEta(),
                    r.providerPeakInFlight(), r.recoverySeconds() < 0 ? "never" : r.recoverySeconds() + " s");
        }
    }
}
