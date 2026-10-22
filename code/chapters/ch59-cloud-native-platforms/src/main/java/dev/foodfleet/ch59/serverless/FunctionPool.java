package dev.foodfleet.ch59.serverless;

import java.util.PriorityQueue;

/**
 * How a function platform (AWS Lambda, Azure Functions, Cloud Run functions with concurrency 1) serves a stream of
 * requests: each instance handles one request at a time; a request finding no idle instance starts a new one and
 * pays the cold start; an instance idle for longer than the keep-warm period is reclaimed. Replays an arrival trace
 * and counts what that costs.
 */
public final class FunctionPool {

    public record Result(long requests, long coldStarts, double busySeconds, int peakInstances, double coldStartShare) {
    }

    private FunctionPool() {
    }

    /**
     * @param arrivals      request arrival times, seconds, ascending
     * @param serviceSecs   how long one request keeps an instance busy, warm
     * @param coldSecs      the extra time a cold start adds (runtime and application start)
     * @param keepWarmSecs  how long an idle instance is kept before it's reclaimed
     * @param provisioned   instances kept warm at all times (provisioned concurrency, minimum instances)
     */
    public static Result replay(double[] arrivals, double serviceSecs, double coldSecs, double keepWarmSecs, int provisioned) {
        // an instance: when it's free again, and whether it's provisioned (never reclaimed)
        record Instance(double freeAt, boolean pinned) {
        }
        PriorityQueue<Instance> busy = new PriorityQueue<>((a, b) -> Double.compare(a.freeAt(), b.freeAt()));
        // idle instances, most recently freed first, as platforms reuse the warmest one
        PriorityQueue<Instance> idle = new PriorityQueue<>((a, b) -> Double.compare(b.freeAt(), a.freeAt()));
        for (int k = 0; k < provisioned; k++) {
            idle.add(new Instance(0, true));
        }
        long cold = 0;
        double busySecs = 0;
        int peak = provisioned;
        for (double t : arrivals) {
            while (!busy.isEmpty() && busy.peek().freeAt() <= t) {
                idle.add(busy.poll());
            }
            idle.removeIf(i -> !i.pinned() && t - i.freeAt() > keepWarmSecs);
            Instance used = idle.poll();
            double start = t;
            if (used == null) {
                cold++;
                start = t + coldSecs;
                busySecs += coldSecs;
            }
            busySecs += serviceSecs;
            busy.add(new Instance(start + serviceSecs, used != null && used.pinned()));
            peak = Math.max(peak, busy.size() + idle.size());
        }
        return new Result(arrivals.length, cold, busySecs, peak, arrivals.length == 0 ? 0 : (double) cold / arrivals.length);
    }
}
