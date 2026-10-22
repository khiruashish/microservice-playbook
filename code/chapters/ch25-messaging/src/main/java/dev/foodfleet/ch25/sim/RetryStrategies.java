package dev.foodfleet.ch25.sim;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What a consumer does when a message fails, compared on one partition of order events for
 * 180 s (100 events/s, 3 events per order, a few seconds apart). Two things go wrong:
 * <ul>
 *   <li>at 20 s, one <b>poison message</b> (it can never be processed: invalid data);</li>
 *   <li>from 60 s to 90 s, the <b>dependency is down</b>: every call fails after a 200 ms timeout.</li>
 * </ul>
 * A successful attempt takes 4 ms. The simulation runs until 600 s so backlogs can drain.
 * In the {@link Scenario#PARTIAL_OUTAGE} variant only one restaurant partner's API is down, so only
 * 5% of messages (every 20th order) fail during the outage.
 */
public final class RetryStrategies {

    public enum Strategy {
        /** Retry every second until it works; never gives up. */
        RETRY_FOREVER_BLOCKING("Retry forever (blocking)"),
        /** Spring Kafka's DefaultErrorHandler with no configuration: 10 attempts, no delay, then log and skip. */
        DEFAULT_TEN_ATTEMPTS_THEN_SKIP("10 quick attempts, then log and skip"),
        /** Exponential backoff 1, 2, 4, 8, 16 s on the partition, then dead-letter topic. */
        BLOCKING_BACKOFF_THEN_DLT("Blocking backoff (31 s), then DLT"),
        /** The same, but invalid messages are classified as not retryable and go straight to the DLT. */
        BLOCKING_BACKOFF_CLASSIFIED("Blocking backoff + not-retryable to DLT"),
        /** Failed messages leave the partition for retry topics (1 s, 10 s, 60 s), then the DLT; classified. */
        RETRY_TOPICS_CLASSIFIED("Retry topics 1s/10s/60s + not-retryable to DLT");

        public final String label;

        Strategy(String label) {
            this.label = label;
        }
    }

    public enum Scenario { FULL_OUTAGE, PARTIAL_OUTAGE }

    public record Result(Strategy strategy, int delivered, int lost, int poisonInDlt, int goodInDlt, int unprocessed,
                         double p99LatencyMs, double maxLatencyMs, int orderViolations) {
    }

    static final int OK_MS = 4;
    static final int TIMEOUT_MS = 200;
    static final long OUTAGE_FROM = 60_000;
    static final long OUTAGE_TO = 90_000;
    static final long END = 600_000;
    static final int ORDERS = 6_000;

    record Event(int offset, String key, int order, int seq, long arrival, boolean poison) {
    }

    /** 6,000 orders, one every 30 ms; each emits events at +0, +2 s, +5 s. One poison event near 20 s. */
    static List<Event> stream() {
        List<long[]> raw = new ArrayList<>();
        for (int o = 0; o < ORDERS; o++) {
            long start = o * 30L;
            raw.add(new long[] {start, o, 0});
            raw.add(new long[] {start + 2_000, o, 1});
            raw.add(new long[] {start + 5_000, o, 2});
        }
        raw.sort((a, b) -> a[0] != b[0] ? Long.compare(a[0], b[0]) : Long.compare(a[1], b[1]));
        List<Event> events = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            long[] r = raw.get(i);
            events.add(new Event(i, "o-" + r[1], (int) r[1], (int) r[2], r[0], r[1] == 600 && r[2] == 1));   // order 600's 2nd event, ~20 s
        }
        return events;
    }

    /** Outcome of one attempt started at {@code t}: null if it succeeded, else the failure. */
    static Failure attempt(Event e, long t, Scenario scenario) {
        if (e.poison()) {
            return Failure.INVALID;
        }
        boolean affected = scenario == Scenario.FULL_OUTAGE || e.order() % 20 == 0;
        return affected && t >= OUTAGE_FROM && t < OUTAGE_TO ? Failure.TIMEOUT : null;
    }

    enum Failure { INVALID, TIMEOUT }

    static int cost(Failure f) {
        return f == null ? OK_MS : f == Failure.INVALID ? OK_MS : TIMEOUT_MS;
    }

    public static Result run(Strategy s, Scenario scenario) {
        List<Event> events = stream();
        long[] doneAt = new long[events.size()];          // success time, or -1
        Arrays.fill(doneAt, -1);
        int lost = 0;
        int poisonDlt = 0;
        int goodDlt = 0;
        int unprocessed = 0;

        if (s == Strategy.RETRY_TOPICS_CLASSIFIED) {
            int[] counts = retryTopics(events, doneAt, scenario);
            poisonDlt = counts[0];
            goodDlt = counts[1];
            unprocessed = counts[2];
        } else {
            long t = 0;
            for (Event e : events) {
                t = Math.max(t, e.arrival());
                if (t >= END) {
                    unprocessed++;
                    continue;
                }
                int attempts = 0;
                long[] backoff = {1_000, 2_000, 4_000, 8_000, 16_000};
                while (true) {
                    Failure f = attempt(e, t, scenario);
                    t += cost(f);
                    attempts++;
                    if (f == null) {
                        doneAt[e.offset()] = t;
                        break;
                    }
                    boolean giveUp;
                    long wait;
                    switch (s) {
                        case RETRY_FOREVER_BLOCKING -> { giveUp = false; wait = 1_000; }
                        case DEFAULT_TEN_ATTEMPTS_THEN_SKIP -> { giveUp = attempts >= 10; wait = 0; }
                        case BLOCKING_BACKOFF_THEN_DLT -> { giveUp = attempts > backoff.length; wait = giveUp ? 0 : backoff[attempts - 1]; }
                        default -> {                                  // BLOCKING_BACKOFF_CLASSIFIED
                            giveUp = f == Failure.INVALID || attempts > backoff.length;
                            wait = giveUp ? 0 : backoff[attempts - 1];
                        }
                    }
                    if (giveUp) {
                        if (s == Strategy.DEFAULT_TEN_ATTEMPTS_THEN_SKIP) {
                            lost += e.poison() ? 0 : 1;               // logged, gone
                        } else if (e.poison()) {
                            poisonDlt++;
                        } else {
                            goodDlt++;
                        }
                        break;
                    }
                    t += wait;
                    if (t >= END) {
                        break;
                    }
                }
                if (t >= END && doneAt[e.offset()] < 0 && !e.poison()) {
                    unprocessed++;
                }
            }
            if (s == Strategy.DEFAULT_TEN_ATTEMPTS_THEN_SKIP) {
                lost += 1;                                            // the poison message, skipped with a log line
            }
        }
        return summarise(s, events, doneAt, lost, poisonDlt, goodDlt, unprocessed);
    }

    /** Main topic never blocks; failures go to the next retry topic, each consumed in due-time order. */
    private static int[] retryTopics(List<Event> events, long[] doneAt, Scenario scenario) {
        long[] delays = {1_000, 10_000, 60_000};
        int poisonDlt = 0;
        int goodDlt = 0;
        int unprocessed = 0;
        List<long[]> next = new ArrayList<>();             // {offset, enqueuedAt}
        long t = 0;
        for (Event e : events) {
            t = Math.max(t, e.arrival());
            if (t >= END) {
                unprocessed++;
                continue;
            }
            Failure f = attempt(e, t, scenario);
            t += cost(f);
            if (f == null) {
                doneAt[e.offset()] = t;
            } else if (f == Failure.INVALID) {
                poisonDlt++;
            } else {
                next.add(new long[] {e.offset(), t});
            }
        }
        for (int stage = 0; stage < delays.length; stage++) {
            List<long[]> failed = new ArrayList<>();
            long consumer = 0;
            for (long[] m : next) {                       // FIFO; all have the same delay, so due times are ordered
                Event e = events.get((int) m[0]);
                long start = Math.max(consumer, m[1] + delays[stage]);
                if (start >= END) {
                    unprocessed++;
                    continue;
                }
                Failure f = attempt(e, start, scenario);
                consumer = start + cost(f);
                if (f == null) {
                    doneAt[e.offset()] = consumer;
                } else {
                    failed.add(new long[] {m[0], consumer});
                }
            }
            next = failed;
        }
        goodDlt += next.size();
        return new int[] {poisonDlt, goodDlt, unprocessed};
    }

    private static Result summarise(Strategy s, List<Event> events, long[] doneAt, int lost, int poisonDlt, int goodDlt,
                                     int unprocessed) {
        List<Long> latencies = new ArrayList<>();
        int delivered = 0;
        Map<String, long[]> perKey = new HashMap<>();
        for (Event e : events) {
            if (doneAt[e.offset()] >= 0) {
                delivered++;
                latencies.add(doneAt[e.offset()] - e.arrival());
            }
            perKey.computeIfAbsent(e.key(), k -> new long[] {-1, -1, -1})[e.seq()] = doneAt[e.offset()];
        }
        int violations = 0;
        for (long[] seq : perKey.values()) {
            for (int k = 1; k < seq.length; k++) {
                // a later event applied while an earlier one was still pending (or never applied)
                if (seq[k] >= 0 && (seq[k - 1] < 0 || seq[k - 1] > seq[k])) {
                    violations++;
                }
            }
        }
        latencies.sort(Long::compare);
        double p99 = latencies.isEmpty() ? 0 : latencies.get((int) (latencies.size() * 0.99));
        double max = latencies.isEmpty() ? 0 : latencies.getLast();
        return new Result(s, delivered, lost, poisonDlt, goodDlt, unprocessed, p99, max, violations);
    }

    public static void main(String[] args) {
        System.out.println("one partition, 18,000 order events over 180 s; poison message at ~20 s; dependency failing 60-90 s");
        for (Scenario scenario : Scenario.values()) {
            System.out.println();
            System.out.println(scenario == Scenario.FULL_OUTAGE ? "FULL OUTAGE: every call fails (200 ms timeout)"
                    : "PARTIAL OUTAGE: calls for 1 in 20 orders fail (one partner's API down)");
            System.out.printf("%-47s %9s %5s %11s %9s %11s %8s %8s %12s%n", "strategy", "delivered", "lost",
                    "poison>DLT", "good>DLT", "unprocessed", "p99 s", "max s", "out of order");
            for (Strategy s : Strategy.values()) {
                Result r = run(s, scenario);
                System.out.printf("%-47s %9d %5d %11d %9d %11d %8.1f %8.1f %12d%n", s.label, r.delivered(), r.lost(),
                        r.poisonInDlt(), r.goodInDlt(), r.unprocessed(), r.p99LatencyMs() / 1000, r.maxLatencyMs() / 1000,
                        r.orderViolations());
            }
        }
    }
}
