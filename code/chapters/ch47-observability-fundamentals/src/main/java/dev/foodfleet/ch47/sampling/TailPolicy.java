package dev.foodfleet.ch47.sampling;

import java.time.Duration;

/**
 * Tail sampling: decide after the whole trace has been seen, so the interesting ones can be kept. FoodFleet's
 * policy, the same as {@code otel/collector.yaml}'s {@code tail_sampling} processor: keep every trace with an
 * error, every trace slower than the threshold, and a fixed share of the rest as a baseline to compare against.
 *
 * @param slowerThan  keep traces whose longest span takes longer than this
 * @param baseline    the share of the remaining traces to keep (by trace ID, so the choice is repeatable)
 */
public record TailPolicy(Duration slowerThan, TraceIdRatio baseline) {

    public enum Decision { KEEP_ERROR, KEEP_SLOW, KEEP_BASELINE, DROP }

    public static TailPolicy foodFleet() {
        return new TailPolicy(Duration.ofMillis(300), new TraceIdRatio(0.01));
    }

    /**
     * @param traceId      the trace's ID
     * @param hasError     whether any span in the trace has an error status
     * @param longestSpan  the duration of the trace's longest span (normally its root)
     */
    public Decision decide(String traceId, boolean hasError, Duration longestSpan) {
        if (hasError) {
            return Decision.KEEP_ERROR;
        }
        if (longestSpan.compareTo(slowerThan) > 0) {
            return Decision.KEEP_SLOW;
        }
        return baseline.sampled(traceId) ? Decision.KEEP_BASELINE : Decision.DROP;
    }
}
