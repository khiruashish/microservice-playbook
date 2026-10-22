package dev.foodfleet.ch25.sim;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;

/**
 * Ordering vs parallelism on one partition: 2,000 order events (500 orders x PLACED, ACCEPTED,
 * PICKED_UP, DELIVERED), already in the partition with events of the same order close together.
 * Processing takes 2-20 ms (most short, some long). Three ways to consume:
 * <ul>
 *   <li>one thread per partition (the default): ordered, slow;</li>
 *   <li>the listener hands records to an 8-thread pool (or 8 competing consumers on a queue):
 *       fast, but events of one order can run concurrently and finish out of order;</li>
 *   <li>8 lanes chosen by hash(order id): parallel across orders, sequential within one. Eight
 *       partitions keyed by order id, one consumer each, produce the same schedule.</li>
 * </ul>
 */
public final class OrderingSimulation {

    public enum Mode { ONE_THREAD_PER_PARTITION, POOL_OF_8, KEY_ORDERED_LANES_8 }

    public record Result(Mode mode, int events, double makespanMs, double eventsPerSecond, int outOfOrder) {
    }

    record Event(int offset, int order, int seq, double serviceMs) {
    }

    static List<Event> events(long seed) {
        Random random = new Random(seed);
        List<Event> events = new ArrayList<>();
        int[] next = new int[500];
        int offset = 0;
        // interleave: a sliding window of ~20 active orders, each advancing one stage at a time
        List<Integer> active = new ArrayList<>();
        int started = 0;
        while (offset < 2_000) {
            while (active.size() < 20 && started < 500) {
                active.add(started++);
            }
            int idx = random.nextInt(active.size());
            int order = active.get(idx);
            double service = random.nextDouble() < 0.9 ? 2 + random.nextDouble() * 4 : 10 + random.nextDouble() * 10;
            events.add(new Event(offset++, order, next[order]++, service));
            if (next[order] == 4) {
                active.remove(idx);
            }
        }
        return events;
    }

    public static Result run(Mode mode, long seed) {
        List<Event> events = events(seed);
        double[] start = new double[events.size()];
        double[] end = new double[events.size()];
        switch (mode) {
            case ONE_THREAD_PER_PARTITION -> {
                double t = 0;
                for (Event e : events) {
                    start[e.offset()] = t;
                    t += e.serviceMs();
                    end[e.offset()] = t;
                }
            }
            case POOL_OF_8 -> {                                // next free worker takes the next record
                PriorityQueue<Double> workers = new PriorityQueue<>();
                for (int w = 0; w < 8; w++) {
                    workers.add(0.0);
                }
                for (Event e : events) {
                    double t = workers.poll();
                    start[e.offset()] = t;
                    end[e.offset()] = t + e.serviceMs();
                    workers.add(end[e.offset()]);
                }
            }
            case KEY_ORDERED_LANES_8 -> {                      // lane = hash(key) % 8
                double[] lane = new double[8];
                for (Event e : events) {
                    int l = Math.floorMod(Integer.hashCode(e.order() * 31 + 7), 8);
                    start[e.offset()] = lane[l];
                    lane[l] += e.serviceMs();
                    end[e.offset()] = lane[l];
                }
            }
        }
        double makespan = 0;
        for (double e : end) {
            makespan = Math.max(makespan, e);
        }
        // out of order: an event started before the previous event of the same order had finished
        int violations = 0;
        double[][] finished = new double[500][4];
        for (Event e : events) {
            finished[e.order()][e.seq()] = end[e.offset()];
        }
        for (Event e : events) {
            if (e.seq() > 0 && start[e.offset()] < finished[e.order()][e.seq() - 1]) {
                violations++;
            }
        }
        return new Result(mode, events.size(), makespan, events.size() / (makespan / 1000), violations);
    }

    public static void main(String[] args) {
        System.out.println("2,000 events of 500 orders on one partition; 2-20 ms each\n");
        System.out.printf("%-26s %12s %12s %16s%n", "consumer", "time ms", "events/s", "out of order");
        for (Mode m : Mode.values()) {
            Result r = run(m, 25);
            System.out.printf("%-26s %12.0f %12.0f %16d%n", m, r.makespanMs(), r.eventsPerSecond(), r.outOfOrder());
        }
    }
}
