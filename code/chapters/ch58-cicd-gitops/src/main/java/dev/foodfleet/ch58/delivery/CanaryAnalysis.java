package dev.foodfleet.ch58.delivery;

/**
 * The decision at each step of a canary, as Argo Rollouts' and Flagger's analyses make it: compare the canary's error
 * rate and latency over the last window with absolute limits and with the stable version, and only once the window
 * has enough requests to mean something.
 */
public final class CanaryAnalysis {

    /** One version's traffic over the analysis window. */
    public record Window(long requests, long errors, double p99Millis) {

        public double errorRate() {
            return requests == 0 ? 0 : (double) errors / requests;
        }
    }

    /**
     * @param maxErrorRate      abort above this error rate, whatever the baseline does
     * @param maxErrorRateDelta abort if the canary's error rate exceeds the stable version's by more than this
     * @param maxP99Millis      abort above this p99
     * @param minRequests       below this many canary requests, keep waiting
     */
    public record Thresholds(double maxErrorRate, double maxErrorRateDelta, double maxP99Millis, long minRequests) {
    }

    public enum Verdict { PROMOTE, WAIT, ABORT }

    private CanaryAnalysis() {
    }

    public static Verdict judge(Window canary, Window stable, Thresholds t) {
        // a burst of errors is enough to abort before the sample is complete: waiting would expose more users
        if (canary.errors() > 0 && canary.errorRate() > t.maxErrorRate() && canary.errors() >= Math.min(10, t.minRequests())) {
            return Verdict.ABORT;
        }
        if (canary.requests() < t.minRequests()) {
            return Verdict.WAIT;
        }
        if (canary.errorRate() > t.maxErrorRate() || canary.errorRate() - stable.errorRate() > t.maxErrorRateDelta()
                || canary.p99Millis() > t.maxP99Millis()) {
            return Verdict.ABORT;
        }
        return Verdict.PROMOTE;
    }
}
