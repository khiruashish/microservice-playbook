package dev.foodfleet.ch68.partition;

import java.util.List;
import java.util.SplittableRandom;

/** What a placement does to a set of keys: how evenly it spreads them, and how many move when nodes change. */
public final class Analysis {

    private Analysis() {
    }

    /** Share of keys whose owner changes when the cluster goes from {@code from} to {@code to} nodes. */
    public static double moved(Placement p, List<String> keys, int from, int to) {
        long moved = keys.stream().filter(k -> p.owner(k, from) != p.owner(k, to)).count();
        return (double) moved / keys.size();
    }

    /** Keys per node. */
    public static long[] spread(Placement p, List<String> keys, int n) {
        long[] perNode = new long[n];
        for (String k : keys) {
            perNode[p.owner(k, n)]++;
        }
        return perNode;
    }

    /** The busiest node's load over the average: 1.0 is perfectly even. */
    public static double maxOverMean(long[] perNode) {
        long max = 0;
        long sum = 0;
        for (long v : perNode) {
            max = Math.max(max, v);
            sum += v;
        }
        return max / ((double) sum / perNode.length);
    }

    /** Ranks 0..n-1 drawn with probability proportional to 1/(rank+1)^s: a few very popular, a long tail. */
    public static final class Zipf {

        private final double[] cdf;

        public Zipf(int n, double s) {
            cdf = new double[n];
            double sum = 0;
            for (int i = 0; i < n; i++) {
                sum += 1 / Math.pow(i + 1, s);
                cdf[i] = sum;
            }
            for (int i = 0; i < n; i++) {
                cdf[i] /= sum;
            }
        }

        public int next(SplittableRandom r) {
            double u = r.nextDouble();
            int lo = 0;
            int hi = cdf.length - 1;
            while (lo < hi) {
                int mid = (lo + hi) >>> 1;
                if (cdf[mid] < u) {
                    lo = mid + 1;
                } else {
                    hi = mid;
                }
            }
            return lo;
        }

        /** The share of all draws that the most popular rank gets. */
        public double top() {
            return cdf[0];
        }
    }
}
