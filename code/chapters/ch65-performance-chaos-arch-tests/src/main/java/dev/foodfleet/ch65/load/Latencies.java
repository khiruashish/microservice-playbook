package dev.foodfleet.ch65.load;

import java.util.Arrays;

/** Recorded latencies in microseconds, and their percentiles. Thread-safe. */
public final class Latencies {

    private long[] values = new long[1 << 14];
    private int size;

    public synchronized void record(long micros) {
        if (size == values.length) {
            values = Arrays.copyOf(values, size * 2);
        }
        values[size++] = micros;
    }

    /**
     * What HdrHistogram's {@code recordValueWithExpectedInterval} does for a closed-model load generator: a request
     * that took longer than the interval it should have been sent at stands for the requests that would have been sent
     * meanwhile and waited too, each a little less.
     */
    public synchronized void recordCorrected(long micros, long expectedIntervalMicros) {
        record(micros);
        for (long missing = micros - expectedIntervalMicros; missing >= expectedIntervalMicros; missing -= expectedIntervalMicros) {
            record(missing);
        }
    }

    public synchronized int count() {
        return size;
    }

    /** The value at a percentile (0-100), in milliseconds. */
    public synchronized double percentile(double p) {
        if (size == 0) {
            return Double.NaN;
        }
        long[] sorted = Arrays.copyOf(values, size);
        Arrays.sort(sorted);
        int index = (int) Math.ceil(p / 100.0 * size) - 1;
        return sorted[Math.max(0, Math.min(size - 1, index))] / 1000.0;
    }

    public synchronized double max() {
        return percentile(100);
    }
}
