package dev.foodfleet.ch59.retry;

import java.time.Duration;

/**
 * The adaptive retry mode's client-side rate limiter, ported from the AWS SDK for Java v2's
 * {@code RateLimiterTokenBucket}: off until the first throttling response; then a token bucket whose rate drops to 70%
 * of the measured send rate on each throttle and grows back along a cubic curve (as in TCP CUBIC) while calls succeed.
 */
public final class ClientRateLimiter {

    static final double MIN_FILL_RATE = 0.5;
    static final double MIN_CAPACITY = 1.0;
    static final double SMOOTH = 0.8;
    static final double BETA = 0.7;
    static final double SCALE_CONSTANT = 0.4;

    private double fillRate;
    private double maxCapacity;
    private double currentCapacity;
    private boolean lastTimestampIsSet;
    private double lastTimestamp;
    private boolean enabled;
    private double measuredTxRate;
    private double lastTxRateBucket;
    private long requestCount;
    private double lastMaxRate;
    private double lastThrottleTime;
    private double timeWindow;

    /** Takes one token, or says how long to wait for it. */
    public synchronized Duration acquire(double now) {
        if (!enabled) {
            return Duration.ZERO;
        }
        refill(now);
        if (currentCapacity < 1) {
            return Duration.ofMillis((long) Math.ceil((1 - currentCapacity) / fillRate * 1000));
        }
        currentCapacity -= 1;
        return Duration.ZERO;
    }

    public synchronized void update(double now, boolean throttled) {
        updateMeasuredRate(now);
        double calculated;
        if (throttled) {
            double rateToUse = enabled ? Math.min(measuredTxRate, fillRate) : measuredTxRate;
            lastMaxRate = rateToUse;
            timeWindow = Math.pow(lastMaxRate * (1 - BETA) / SCALE_CONSTANT, 1.0 / 3);
            lastThrottleTime = now;
            calculated = rateToUse * BETA;
            enabled = true;
        } else {
            timeWindow = Math.pow(lastMaxRate * (1 - BETA) / SCALE_CONSTANT, 1.0 / 3);
            calculated = SCALE_CONSTANT * Math.pow(now - lastThrottleTime - timeWindow, 3) + lastMaxRate;
        }
        double newRate = Math.min(calculated, 2 * measuredTxRate);
        refill(now);
        fillRate = Math.max(newRate, MIN_FILL_RATE);
        maxCapacity = Math.max(newRate, MIN_CAPACITY);
        currentCapacity = Math.min(currentCapacity, maxCapacity);
    }

    public synchronized double fillRate() {
        return enabled ? fillRate : Double.POSITIVE_INFINITY;
    }

    private void refill(double now) {
        if (lastTimestampIsSet) {
            currentCapacity = Math.min(maxCapacity, currentCapacity + (now - lastTimestamp) * fillRate);
        }
        lastTimestamp = now;
        lastTimestampIsSet = true;
    }

    private void updateMeasuredRate(double now) {
        requestCount++;
        double bucket = Math.floor(now * 2) / 2;
        if (bucket > lastTxRateBucket) {
            double currentRate = requestCount / (bucket - lastTxRateBucket);
            measuredTxRate = currentRate * SMOOTH + measuredTxRate * (1 - SMOOTH);
            requestCount = 0;
            lastTxRateBucket = bucket;
        }
    }
}
