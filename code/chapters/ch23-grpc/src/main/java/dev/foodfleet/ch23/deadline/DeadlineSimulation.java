package dev.foodfleet.ch23.deadline;

import java.util.Arrays;
import java.util.Random;

/**
 * Why gRPC deadlines propagate. The tracking screen calls tracking-service with a 500 ms deadline;
 * tracking (20 ms of work) calls eta-service (30 ms) which calls the maps provider, then each adds
 * 10 ms on the way back. During an incident the maps provider takes ~1.5 s for 60% of calls.
 *
 * <ul>
 *   <li><b>Fixed per-hop timeouts</b> (what HTTP stacks usually have): each hop waits up to 1 s for the
 *       next, knowing nothing about the caller's deadline. When the app gives up at 500 ms, the chain
 *       keeps working on a request nobody will read.</li>
 *   <li><b>Propagated deadline</b> (gRPC's default when a server makes calls in the request's Context):
 *       each hop passes the remaining time on, refuses to start work it can't finish, and when the
 *       deadline passes the cancellation travels down the chain within a few milliseconds.</li>
 * </ul>
 * Each request is computed on its own (no queueing), so the numbers isolate the effect of deadlines.
 */
public final class DeadlineSimulation {

    public enum Mode { FIXED_HOP_TIMEOUTS, PROPAGATED_DEADLINE }

    public record Result(Mode mode, int requests, double succeededPercent, double mapsBusyMsPerRequest,
                         double wastedMsPerRequest, double p99HeldMs) {
    }

    static final double CLIENT_DEADLINE = 500;
    static final double HOP_TIMEOUT = 1_000;
    static final double CANCEL_PROPAGATION = 2;      // ms per hop for RST_STREAM to travel down

    public static Result run(Mode mode, int requests, double slowShare, long seed) {
        Random random = new Random(seed);
        int ok = 0;
        double mapsBusy = 0;
        double wasted = 0;
        double[] held = new double[requests];               // how long the chain kept working on it
        for (int r = 0; r < requests; r++) {
            double mapsLatency = random.nextDouble() < slowShare
                    ? 1_500 * (0.8 + 0.4 * random.nextDouble())
                    : 40 * (0.5 + random.nextDouble());
            double mapsStart = 20 + 30;                      // tracking work, then eta work
            double mapsEnd;
            double done;                                     // when the last hop stopped working
            boolean success;
            if (mode == Mode.FIXED_HOP_TIMEOUTS) {
                mapsEnd = mapsStart + mapsLatency;           // maps never hears that anyone gave up
                if (mapsLatency <= HOP_TIMEOUT) {
                    double response = mapsEnd + 10 + 10;     // eta, then tracking, finish up
                    success = response <= CLIENT_DEADLINE;
                    done = response;
                } else {
                    success = false;
                    done = Math.max(mapsEnd, mapsStart + HOP_TIMEOUT);
                }
            } else {
                double remaining = CLIENT_DEADLINE - mapsStart;
                double cancelledAt = CLIENT_DEADLINE + 2 * CANCEL_PROPAGATION;   // tracking -> eta -> maps
                if (mapsLatency + 20 <= remaining) {
                    mapsEnd = mapsStart + mapsLatency;
                    success = true;
                    done = mapsEnd + 20;
                } else {
                    mapsEnd = Math.min(mapsStart + mapsLatency, cancelledAt);   // maps observes cancellation
                    success = false;
                    done = cancelledAt;
                }
            }
            if (success) {
                ok++;
            }
            mapsBusy += mapsEnd - mapsStart;
            wasted += Math.max(0, done - CLIENT_DEADLINE);   // work after the app had already given up
            held[r] = done;
        }
        Arrays.sort(held);
        return new Result(mode, requests, 100.0 * ok / requests, mapsBusy / requests, wasted / requests,
                held[(int) (requests * 0.99)]);
    }

    public static void main(String[] args) {
        System.out.println("tracking -> eta -> maps; app deadline 500 ms; 10,000 requests per row\n");
        System.out.printf("%-22s %-22s %10s %16s %18s %14s%n", "maps behaviour", "mode", "succeeded",
                "maps busy ms/req", "wasted ms/req", "p99 held ms");
        for (double slow : new double[] {0.0, 0.6}) {
            for (Mode mode : Mode.values()) {
                Result r = run(mode, 10_000, slow, 23);
                System.out.printf("%-22s %-22s %9.1f%% %16.0f %18.0f %14.0f%n",
                        slow == 0 ? "healthy" : "60% slow (~1.5 s)", mode, r.succeededPercent(),
                        r.mapsBusyMsPerRequest(), r.wastedMsPerRequest(), r.p99HeldMs());
            }
        }
    }
}
