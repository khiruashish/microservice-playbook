package dev.foodfleet.ch26.experiment;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 200,000 tracking clients lose their connections at once (a load balancer restart, a network
 * blip) and reconnect. The gateway fleet can complete 20,000 handshakes per second (TLS + token
 * check). A client gives up on an attempt after 5 s; the server doesn't know, and still spends a
 * handshake on it when it reaches the front of the queue. That wasted work is what turns a burst
 * into a storm. Four reconnect policies:
 * <ul>
 *   <li>immediately, then every 1 s after a failure;</li>
 *   <li>a fixed 3 s delay (what a browser's EventSource does by default; no randomness);</li>
 *   <li>exponential backoff 1, 2, 4, 8 ... s, no jitter;</li>
 *   <li>exponential backoff with full jitter: a random delay between 0 and the backoff value; the
 *       first reconnect spread over 10 s (the fleet needs 10 s to accept everyone anyway), as a server
 *       can arrange by sending each client a randomised SSE {@code retry:} value;</li>
 *   <li>immediately, then every 1 s, but the server checks an attempt's age first and drops the ones
 *       whose client has already given up, instead of doing the handshake.</li>
 * </ul>
 */
public final class ReconnectStorm {

    public enum Policy { IMMEDIATE_THEN_1S, FIXED_3S, EXPONENTIAL_NO_JITTER, EXPONENTIAL_FULL_JITTER, IMMEDIATE_SERVER_SHEDS_STALE }

    public record Result(Policy policy, double secondsTo99Percent, int peakAttemptsPerSecond, double attemptsPerClient,
                         int wastedHandshakes) {
    }

    static final int CLIENTS = 200_000;
    static final int HANDSHAKES_PER_SECOND = 20_000;
    static final int TICK_MS = 10;
    static final int CLIENT_TIMEOUT_MS = 5_000;
    static final int MAX_MS = 300_000;

    public static Result run(Policy policy, long seed) {
        Random random = new Random(seed);
        int ticks = MAX_MS / TICK_MS;
        List<List<Integer>> attemptsAt = new ArrayList<>(ticks);     // client ids starting an attempt at tick t
        for (int t = 0; t < ticks; t++) {
            attemptsAt.add(new ArrayList<>());
        }
        int[] failures = new int[CLIENTS];
        for (int c = 0; c < CLIENTS; c++) {
            int first = switch (policy) {
                case IMMEDIATE_THEN_1S, IMMEDIATE_SERVER_SHEDS_STALE -> random.nextInt(100);   // network jitter only
                case FIXED_3S -> 3_000 + random.nextInt(100);
                case EXPONENTIAL_NO_JITTER -> 1_000 + random.nextInt(100);
                case EXPONENTIAL_FULL_JITTER -> random.nextInt(10_000);
            };
            attemptsAt.get(first / TICK_MS).add(c);
        }
        ArrayDeque<int[]> queue = new ArrayDeque<>();                     // {client, startTick}
        int perTick = HANDSHAKES_PER_SECOND * TICK_MS / 1000;
        int connected = 0;
        int attempts = 0;
        int wasted = 0;
        int peak = 0;
        int windowAttempts = 0;
        double to99 = -1;
        for (int t = 0; t < ticks; t++) {
            for (int c : attemptsAt.get(t)) {
                queue.add(new int[] {c, t});
                attempts++;
                windowAttempts++;
            }
            if (t % (1000 / TICK_MS) == (1000 / TICK_MS) - 1) {
                peak = Math.max(peak, windowAttempts);
                windowAttempts = 0;
            }
            // clients whose attempt has just timed out schedule their next one
            for (int[] a : queue) {
                if ((t - a[1]) * TICK_MS == CLIENT_TIMEOUT_MS + TICK_MS) {
                    int c = a[0];
                    int n = ++failures[c];
                    int delay = switch (policy) {
                        case IMMEDIATE_THEN_1S, IMMEDIATE_SERVER_SHEDS_STALE -> 1_000;
                        case FIXED_3S -> 3_000;
                        case EXPONENTIAL_NO_JITTER -> Math.min(30_000, 1_000 << Math.min(n, 5));
                        case EXPONENTIAL_FULL_JITTER -> random.nextInt(Math.min(30_000, 1_000 << Math.min(n, 5)));
                    };
                    int next = t + 1 + delay / TICK_MS;
                    if (next < ticks) {
                        attemptsAt.get(next).add(c);
                    }
                }
            }
            for (int served = 0; served < perTick && !queue.isEmpty(); ) {
                int[] a = queue.poll();
                int age = (t - a[1]) * TICK_MS;
                if (age <= CLIENT_TIMEOUT_MS) {
                    connected++;
                    served++;
                } else if (policy != Policy.IMMEDIATE_SERVER_SHEDS_STALE) {
                    wasted++;                                             // the client already gave up
                    served++;
                }                                                         // else: dropped without a handshake
            }
            if (to99 < 0 && connected >= CLIENTS * 0.99) {
                to99 = (t + 1) * TICK_MS / 1000.0;
            }
        }
        return new Result(policy, to99, peak, attempts / (double) CLIENTS, wasted);
    }

    public static void main(String[] args) {
        System.out.println("200,000 clients reconnect at once; fleet capacity 20,000 handshakes/s; client timeout 5 s\n");
        System.out.printf("%-30s %16s %18s %18s %16s%n", "reconnect policy", "99% back after", "peak attempts/s",
                "attempts/client", "wasted handshakes");
        for (Policy p : Policy.values()) {
            Result r = run(p, 26);
            System.out.printf("%-30s %15.1fs %18d %18.2f %16d%n", p, r.secondsTo99Percent(), r.peakAttemptsPerSecond(),
                    r.attemptsPerClient(), r.wastedHandshakes());
        }
    }
}
