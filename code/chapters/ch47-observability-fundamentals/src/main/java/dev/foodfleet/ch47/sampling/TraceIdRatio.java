package dev.foodfleet.ch47.sampling;

/**
 * Head sampling by trace ID, the rule OpenTelemetry Java's {@code TraceIdRatioBasedSampler} uses: keep the trace if
 * the absolute value of the trace ID's lower 64 bits is below {@code ratio × Long.MAX_VALUE}. Every service that sees
 * the same trace ID makes the same decision without talking to the others, which is what keeps sampled traces whole.
 * The decision is made when the trace starts, before anyone knows whether it will be slow or fail.
 */
public final class TraceIdRatio {

    private final double ratio;
    private final long idUpperBound;

    public TraceIdRatio(double ratio) {
        if (ratio < 0.0 || ratio > 1.0 || Double.isNaN(ratio)) {
            throw new IllegalArgumentException("ratio must be in [0, 1]: " + ratio);
        }
        this.ratio = ratio;
        // the SDK's bounds: exactly 0 and Long.MAX_VALUE at the ends, so 0.0 keeps nothing and 1.0 keeps everything
        this.idUpperBound = ratio == 0.0 ? Long.MIN_VALUE : ratio == 1.0 ? Long.MAX_VALUE : (long) (ratio * Long.MAX_VALUE);
    }

    public boolean sampled(String traceId) {
        if (traceId.length() != 32) {
            throw new IllegalArgumentException("trace-id must be 32 hex digits: " + traceId);
        }
        long low = Long.parseUnsignedLong(traceId.substring(16), 16);
        return Math.abs(low) < idUpperBound;
    }

    public double ratio() {
        return ratio;
    }
}
