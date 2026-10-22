package dev.foodfleet.ch70.rightsize;

import java.util.Arrays;

/**
 * Request recommendations the way the Vertical Pod Autoscaler's recommender makes them (VPA 1.8.0's defaults): the CPU
 * target is the 90th percentile of usage samples, memory's is the 90th percentile of the peaks in each aggregation
 * interval (24 h by default; shorter here, since the day is compressed), both plus a 15% safety margin, and at least
 * 25 millicores and 250 MiB. The lower and upper bounds use the 50th and 95th percentiles.
 * <p>
 * Simplified: VPA's histograms decay old samples (half-life 24 h) and weight CPU samples by the request; with the
 * short, uniform histories here neither changes the result much.
 */
public final class Recommender {

    public static final double TARGET = 0.90;
    public static final double LOWER = 0.50;
    public static final double UPPER = 0.95;
    public static final double SAFETY_MARGIN = 0.15;
    public static final double MIN_CPU_CORES = 0.025;
    public static final double MIN_MEMORY_MIB = 250;

    public record Recommendation(double cpuLower, double cpuTarget, double cpuUpper, double memLowerMiB, double memTargetMiB, double memUpperMiB) {
    }

    private Recommender() {
    }

    /**
     * @param cpuCores    CPU usage samples, in cores
     * @param memoryMiB   memory samples, in MiB, one per sampling period
     * @param peakWindow  how many memory samples make one aggregation interval (its peak is one memory sample)
     */
    public static Recommendation recommend(double[] cpuCores, double[] memoryMiB, int peakWindow) {
        double[] peaks = peaks(memoryMiB, peakWindow);
        return new Recommendation(
                Math.max(MIN_CPU_CORES, percentile(cpuCores, LOWER) * (1 + SAFETY_MARGIN)),
                Math.max(MIN_CPU_CORES, percentile(cpuCores, TARGET) * (1 + SAFETY_MARGIN)),
                Math.max(MIN_CPU_CORES, percentile(cpuCores, UPPER) * (1 + SAFETY_MARGIN)),
                Math.max(MIN_MEMORY_MIB, percentile(peaks, LOWER) * (1 + SAFETY_MARGIN)),
                Math.max(MIN_MEMORY_MIB, percentile(peaks, TARGET) * (1 + SAFETY_MARGIN)),
                Math.max(MIN_MEMORY_MIB, percentile(peaks, UPPER) * (1 + SAFETY_MARGIN)));
    }

    /** The maximum of each consecutive window of {@code window} samples. */
    public static double[] peaks(double[] samples, int window) {
        int n = (samples.length + window - 1) / window;
        double[] out = new double[n];
        for (int i = 0; i < n; i++) {
            double max = 0;
            for (int j = i * window; j < Math.min(samples.length, (i + 1) * window); j++) {
                max = Math.max(max, samples[j]);
            }
            out[i] = max;
        }
        return out;
    }

    /** Nearest-rank percentile, p in [0, 1]. */
    public static double percentile(double[] samples, double p) {
        if (samples.length == 0) {
            return 0;
        }
        double[] s = samples.clone();
        Arrays.sort(s);
        int i = (int) Math.ceil(p * s.length) - 1;
        return s[Math.max(0, Math.min(s.length - 1, i))];
    }

    public static double mean(double[] samples) {
        return Arrays.stream(samples).average().orElse(0);
    }
}
