package dev.foodfleet.ch76.platform;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.DoubleUnaryOperator;

/**
 * A day of requests on three kinds of platform, driven by start-up and service times measured on real processes.
 * <ul>
 *   <li>{@link #faas}: one request per instance at a time; an idle instance is reused, or a new one cold-starts; idle
 *       instances are reclaimed after a keep-alive. Billed per request, cold-start initialisation included.</li>
 *   <li>{@link #scaleToZero}: instances serve many requests; every 2 s the lab sizes them from the request rate
 *       over a 60 s window at 70% of an instance's measured capacity, and removes the last one after 60 s without
 *       requests plus a 30 s grace period (Knative's default window, target and grace); a request that finds no instance waits for a cold start.
 *       Billed per instance-second, one minute minimum per instance.</li>
 *   <li>{@link #alwaysOn}: enough instances for the day's peak at 70%, at least two, all day.</li>
 * </ul>
 * Queueing inside an instance isn't modelled: a warm request takes a measured warm service time.
 */
public final class Simulation {

    public record Measured(double[] coldMillis, double[] initMillis, double[] warmMillis) {
        double cold(Random r) {
            return coldMillis[r.nextInt(coldMillis.length)];
        }

        double init(Random r) {
            return initMillis[r.nextInt(initMillis.length)];
        }

        double warm(Random r) {
            return warmMillis[r.nextInt(warmMillis.length)];
        }

        /** Requests per second one instance (one vCPU) can serve, from the median warm time. */
        double capacity() {
            double[] s = warmMillis.clone();
            Arrays.sort(s);
            return 1_000 / s[s.length / 2];
        }
    }

    public record Result(int requests, int cold, double p50, double p99, double max, double instanceHours, double cost) {
    }

    private Simulation() {
    }

    /** Arrival times (s) over a day from a rate function (requests per second at second t), by thinning a Poisson process. */
    public static double[] arrivals(DoubleUnaryOperator rate, double maxRate, long seed) {
        Random r = new Random(seed);
        List<Double> out = new ArrayList<>();
        double t = 0;
        while (true) {
            t += -Math.log(1 - r.nextDouble()) / maxRate;
            if (t >= 86_400) {
                break;
            }
            if (r.nextDouble() * maxRate <= rate.applyAsDouble(t)) {
                out.add(t);
            }
        }
        return out.stream().mapToDouble(Double::doubleValue).toArray();
    }

    public static Result faas(double[] arrivals, Measured m, double keepAliveSeconds, long seed) {
        Random r = new Random(seed);
        List<double[]> instances = new ArrayList<>();   // {busyUntil, lastFinished}
        double[] lat = new double[arrivals.length];
        int cold = 0;
        double cost = 0;
        double busySeconds = 0;
        for (int i = 0; i < arrivals.length; i++) {
            double t = arrivals[i];
            instances.removeIf(x -> x[0] <= t && t - x[1] > keepAliveSeconds);
            double[] pick = null;
            for (double[] x : instances) {
                if (x[0] <= t && (pick == null || x[1] > pick[1])) {
                    pick = x;   // the most recently used idle instance
                }
            }
            double ms;
            double billed;
            if (pick == null) {
                cold++;
                ms = m.cold(r);
                billed = ms;   // INIT plus the first request
                pick = new double[2];
                instances.add(pick);
            } else {
                ms = m.warm(r);
                billed = ms;
            }
            pick[0] = t + ms / 1_000;
            pick[1] = pick[0];
            lat[i] = ms;
            cost += Prices.lambda(billed);
            busySeconds += billed / 1_000;
        }
        return result(lat, cold, busySeconds / 3_600, cost);
    }

    public static Result scaleToZero(double[] arrivals, Measured m, long seed) {
        Random r = new Random(seed);
        double capacity = 0.7 * m.capacity();
        double[] lat = new double[arrivals.length];
        int cold = 0;
        java.util.ArrayDeque<Double> started = new java.util.ArrayDeque<>();   // start times of running instances
        double billed = 0;            // instance-seconds, each instance at least one minute
        double readyAt = 0;           // when the instance started from zero is ready
        double lastRequest = -1e9;
        int head = 0;                 // arrivals in the 60 s window: [head, i)
        int i = 0;
        for (double tick = 0; tick < 86_400 + 120; tick += 2) {
            while (i < arrivals.length && arrivals[i] < tick + 2) {
                double t = arrivals[i];
                if (started.isEmpty()) {
                    cold++;
                    started.push(t);
                    readyAt = t + m.init(r) / 1_000;
                    lat[i] = m.cold(r);
                } else if (t < readyAt) {
                    lat[i] = (readyAt - t) * 1_000 + m.warm(r);   // waits in the activator for the cold start
                } else {
                    lat[i] = m.warm(r);
                }
                lastRequest = t;
                i++;
            }
            double now = tick + 2;
            while (head < i && arrivals[head] < now - 60) {
                head++;
            }
            int desired = now - lastRequest > 60 + 30 ? 0 : Math.max(1, (int) Math.ceil((i - head) / 60.0 / capacity));
            while (started.size() < desired) {
                started.push(now);
            }
            while (started.size() > desired) {
                billed += Math.max(60, now - started.pop());
            }
        }
        while (!started.isEmpty()) {
            billed += Math.max(60, 86_400 + 120 - started.pop());
        }
        return result(lat, cold, billed / 3_600, billed * Prices.INSTANCE_SECOND);
    }

    public static Result alwaysOn(double[] arrivals, Measured m, double peakRate, long seed) {
        Random r = new Random(seed);
        int n = Math.max(2, (int) Math.ceil(peakRate / (0.7 * m.capacity())));
        double[] lat = new double[arrivals.length];
        for (int i = 0; i < lat.length; i++) {
            lat[i] = m.warm(r);
        }
        return result(lat, 0, n * 24.0, n * 86_400 * Prices.INSTANCE_SECOND);
    }

    static Result result(double[] lat, int cold, double hours, double cost) {
        double[] s = lat.clone();
        Arrays.sort(s);
        return new Result(lat.length, cold, pct(s, 50), pct(s, 99), s.length == 0 ? 0 : s[s.length - 1], hours, cost);
    }

    static double pct(double[] sorted, double p) {
        return sorted.length == 0 ? 0 : sorted[Math.max(0, Math.min(sorted.length - 1, (int) Math.ceil(p / 100 * sorted.length) - 1))];
    }
}
