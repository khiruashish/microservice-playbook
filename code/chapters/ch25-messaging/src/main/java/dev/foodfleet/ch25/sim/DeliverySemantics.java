package dev.foodfleet.ch25.sim;

import java.util.Random;

/**
 * When the consumer commits (acknowledges) decides what a crash costs. 100,000 messages; the
 * consumer process crashes on average once every 5,000 messages, at a random point (before,
 * during or after processing). After a crash it restarts from the last committed offset.
 * <ul>
 *   <li>commit before processing: <b>at-most-once</b> (crashes lose messages);</li>
 *   <li>commit after each message: <b>at-least-once</b> (a crash redelivers at most one);</li>
 *   <li>commit after every 500 messages (batch / periodic commit): at-least-once, up to 500 redelivered;</li>
 *   <li>commit after, plus an idempotent consumer (inbox, chapter 15): <b>effectively once</b>.</li>
 * </ul>
 */
public final class DeliverySemantics {

    public enum Mode { COMMIT_BEFORE_PROCESSING, COMMIT_AFTER_EACH, COMMIT_EVERY_500, COMMIT_AFTER_EACH_IDEMPOTENT }

    public record Result(Mode mode, int crashes, int lost, int duplicateEffects, int redeliveries) {
    }

    public static Result run(Mode mode, int messages, long seed) {
        Random random = new Random(seed);
        int[] effects = new int[messages];
        boolean[] seen = new boolean[messages];          // the inbox, committed with the effect
        int committed = 0;                                // next offset to read after a restart
        int position = 0;
        int crashes = 0;
        int redeliveries = 0;
        int batch = mode == Mode.COMMIT_EVERY_500 ? 500 : 1;
        int[] deliveries = new int[messages];
        while (position < messages) {
            int m = position;
            deliveries[m]++;
            if (deliveries[m] > 1) {
                redeliveries++;
            }
            boolean crash = random.nextInt(5_000) == 0;
            int crashPoint = random.nextInt(3);           // 0 before processing, 1 after effect, 2 after commit
            if (mode == Mode.COMMIT_BEFORE_PROCESSING) {
                committed = m + 1;
                if (crash && crashPoint == 0) {           // committed, never processed
                    crashes++;
                    position = committed;
                    continue;
                }
                effects[m]++;
                if (crash && crashPoint == 1) {           // processed and committed: nothing lost
                    crashes++;
                    position = committed;
                    continue;
                }
            } else {
                if (crash && crashPoint == 0) {
                    crashes++;
                    position = committed;
                    continue;
                }
                if (mode == Mode.COMMIT_AFTER_EACH_IDEMPOTENT) {
                    if (!seen[m]) {
                        seen[m] = true;                   // same transaction as the effect
                        effects[m]++;
                    }
                } else {
                    effects[m]++;
                }
                if (crash && crashPoint == 1) {           // processed, not committed: redelivered
                    crashes++;
                    position = committed;
                    continue;
                }
                if ((m + 1) % batch == 0 || m + 1 == messages) {
                    committed = m + 1;
                }
            }
            position = m + 1;
            if (crash && crashPoint == 2) {
                crashes++;
                position = committed;
            }
        }
        int lost = 0;
        int dup = 0;
        for (int e : effects) {
            if (e == 0) {
                lost++;
            } else if (e > 1) {
                dup += e - 1;
            }
        }
        return new Result(mode, crashes, lost, dup, redeliveries);
    }

    public static void main(String[] args) {
        System.out.println("100,000 messages; a crash every ~5,000 on average, at a random point\n");
        System.out.printf("%-30s %8s %8s %18s %13s%n", "commit", "crashes", "lost", "duplicate effects", "redelivered");
        for (Mode m : Mode.values()) {
            Result r = run(m, 100_000, 15);
            System.out.printf("%-30s %8d %8d %18d %13d%n", m, r.crashes(), r.lost(), r.duplicateEffects(), r.redeliveries());
        }
    }
}
