package dev.foodfleet.ch12.composition;

/**
 * Back-of-envelope latency for the two composers, given per-call latencies in milliseconds.
 * Batch calls are assumed to cost a fixed base plus a small per-id amount.
 */
public record LatencyModel(double orderingMs, double lookupMs, double perIdMs) {

    public double naiveMs(int rows) {
        return orderingMs + rows * 2 * (lookupMs + perIdMs);
    }

    public double batchedParallelMs(int rows) {
        return orderingMs + lookupMs + rows * perIdMs;
    }
}
