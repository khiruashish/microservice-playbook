package dev.foodfleet.ch45.chaos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** What users experienced in one observation window: the input to a steady-state hypothesis. */
public record Window(long requests, long succeeded, long p99Millis) {

    public static final Window EMPTY = new Window(0, 0, 0);

    public double successRate() {
        return requests == 0 ? 1.0 : (double) succeeded / requests;
    }

    /** Collects outcomes for the current window; {@link #close()} hands the window over and starts the next. */
    public static final class Recorder {
        private List<Long> latencies = new ArrayList<>();
        private long requests;
        private long succeeded;

        public synchronized void record(long millis, boolean ok) {
            requests++;
            if (ok) {
                succeeded++;
            }
            latencies.add(millis);
        }

        public synchronized Window close() {
            List<Long> l = latencies;
            Window w = new Window(requests, succeeded, percentile(l, 99));
            latencies = new ArrayList<>();
            requests = 0;
            succeeded = 0;
            return w;
        }

        private static long percentile(List<Long> values, double p) {
            if (values.isEmpty()) {
                return 0;
            }
            Collections.sort(values);
            int i = (int) Math.ceil(p / 100.0 * values.size()) - 1;
            return values.get(Math.max(0, Math.min(values.size() - 1, i)));
        }
    }
}
