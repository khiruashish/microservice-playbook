package dev.foodfleet.ch28.dedup;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

/**
 * How long a consumer must remember what it has processed. notification-service handles 500
 * events/s on 6 instances. Duplicates reach it for four reasons, each with its own delay:
 * <ul>
 *   <li>producer retry after a lost acknowledgement: 0.1-2 s, same partition, same instance;</li>
 *   <li>redelivery after a rebalance: 1-30 s, and the partition may now be on another instance;</li>
 *   <li>outbox relay re-publishing after a crash: 1-10 minutes;</li>
 *   <li>a DLT or backfill replay: 1-3 days.</li>
 * </ul>
 * Five deduplication approaches are compared by how many duplicates slip through, and how many
 * event ids each has to keep (the version approach adds one column to rows the consumer already has).
 */
public final class DedupWindows {

    public enum Store {
        NONE("no deduplication", 0),
        LOCAL_LRU_100K("in-memory LRU per instance, 100k ids", 0),
        SHARED_TTL_1H("shared cache (Redis), 1 h TTL", 3_600),
        INBOX_TABLE_7D("inbox table, 7-day retention", 7 * 86_400),
        SHARED_1H_PLUS_VERSION("1 h cache + version on the order row", 3_600);

        public final String label;
        public final long retentionSeconds;

        Store(String label, long retentionSeconds) {
            this.label = label;
            this.retentionSeconds = retentionSeconds;
        }
    }

    public enum Cause { PRODUCER_RETRY, REBALANCE, OUTBOX_REPUBLISH, REPLAY }

    public record Result(Store store, Map<Cause, Integer> applied, Map<Cause, Integer> total, long idsKept) {
        public int appliedTotal() {
            return applied.values().stream().mapToInt(Integer::intValue).sum();
        }
    }

    static final int RATE_PER_SECOND = 500;
    static final int INSTANCES = 6;
    static final int LRU_CAPACITY = 100_000;
    static final Map<Cause, Integer> DUPLICATES = Map.of(Cause.PRODUCER_RETRY, 5_000, Cause.REBALANCE, 3_000,
            Cause.OUTBOX_REPUBLISH, 500, Cause.REPLAY, 1_000);

    public static Result run(Store store, long seed) {
        Random random = new Random(seed);
        Map<Cause, Integer> applied = new LinkedHashMap<>();
        Map<Cause, Integer> total = new LinkedHashMap<>();
        // Each instance sees 1/6 of the traffic, so its LRU remembers about 100k / 83 per s = 1,200 s.
        double lruSeconds = LRU_CAPACITY / (RATE_PER_SECOND / (double) INSTANCES);
        for (Cause cause : Cause.values()) {
            int n = DUPLICATES.get(cause);
            int slipped = 0;
            for (int i = 0; i < n; i++) {
                double delay = switch (cause) {
                    case PRODUCER_RETRY -> 0.1 + random.nextDouble() * 1.9;
                    case REBALANCE -> 1 + random.nextDouble() * 29;
                    case OUTBOX_REPUBLISH -> 60 + random.nextDouble() * 540;
                    case REPLAY -> 86_400 + random.nextDouble() * 2 * 86_400;
                };
                boolean sameInstance = cause != Cause.REBALANCE || random.nextInt(INSTANCES) == 0;
                boolean remembered = switch (store) {
                    case NONE -> false;
                    case LOCAL_LRU_100K -> sameInstance && delay < lruSeconds;
                    case SHARED_TTL_1H, INBOX_TABLE_7D -> delay < store.retentionSeconds;
                    // Old duplicates are caught by the entity itself: each event carries the order's
                    // version, and the consumer's own row records the last version it acted on.
                    case SHARED_1H_PLUS_VERSION -> true;
                };
                if (!remembered) {
                    slipped++;
                }
            }
            applied.put(cause, slipped);
            total.put(cause, n);
        }
        long kept = switch (store) {
            case NONE -> 0;
            case LOCAL_LRU_100K -> (long) LRU_CAPACITY * INSTANCES;
            default -> RATE_PER_SECOND * store.retentionSeconds;
        };
        return new Result(store, applied, total, kept);
    }

    public static void main(String[] args) {
        System.out.println("500 events/s on 6 instances; 9,500 duplicates by cause; duplicates that get applied twice\n");
        System.out.printf("%-42s %10s %10s %10s %10s %8s %14s%n", "dedup store", "retry", "rebalance", "republish",
                "replay", "total", "ids kept");
        System.out.printf("%-42s %10s %10s %10s %10s %8s %14s%n", "(duplicates)", 5_000, 3_000, 500, 1_000, 9_500, "");
        for (Store s : Store.values()) {
            Result r = run(s, 28);
            System.out.printf("%-42s %10d %10d %10d %10d %8d %,14d%n", s.label, r.applied().get(Cause.PRODUCER_RETRY),
                    r.applied().get(Cause.REBALANCE), r.applied().get(Cause.OUTBOX_REPUBLISH), r.applied().get(Cause.REPLAY),
                    r.appliedTotal(), r.idsKept());
        }
    }
}
