package dev.foodfleet.ch68.partition;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which of {@code n} nodes (or partitions, or shards) owns a key. Four common answers, with the properties that
 * matter when n changes: how many keys move, and how evenly keys spread.
 */
public sealed interface Placement permits Placement.Modulo, Placement.Ring, Placement.Rendezvous, Placement.Jump {

    String name();

    int owner(String key, int n);

    /** 64-bit FNV-1a, then MurmurHash3's finaliser so nearby keys land far apart. */
    static long hash(String s) {
        long h = 0xcbf29ce484222325L;
        for (byte b : s.getBytes(StandardCharsets.UTF_8)) {
            h ^= b;
            h *= 0x100000001b3L;
        }
        return mix(h);
    }

    static long mix(long h) {
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= h >>> 33;
        return h;
    }

    /** hash(key) mod n: even, but changing n moves almost every key. */
    record Modulo() implements Placement {

        @Override
        public String name() {
            return "hash mod n";
        }

        @Override
        public int owner(String key, int n) {
            return (int) Math.floorMod(hash(key), (long) n);
        }
    }

    /**
     * Consistent hashing: each node takes {@code vnodes} points on a ring of hashes, and a key belongs to the first
     * point at or after its own hash. Adding a node moves only the keys it takes over (Cassandra's tokens, Dynamo).
     */
    final class Ring implements Placement {

        private final int vnodes;
        private final Map<Integer, long[][]> rings = new ConcurrentHashMap<>();

        public Ring(int vnodes) {
            this.vnodes = vnodes;
        }

        @Override
        public String name() {
            return "consistent hashing, " + vnodes + (vnodes == 1 ? " point per node" : " points per node");
        }

        long[][] ring(int n) {
            return rings.computeIfAbsent(n, k -> {
                long[][] points = new long[k * vnodes][];
                for (int node = 0; node < k; node++) {
                    for (int v = 0; v < vnodes; v++) {
                        points[node * vnodes + v] = new long[] {hash("node-" + node + "#" + v), node};
                    }
                }
                Arrays.sort(points, (a, b) -> Long.compare(a[0], b[0]));
                return points;
            });
        }

        @Override
        public int owner(String key, int n) {
            long[][] points = ring(n);
            long h = hash(key);
            int lo = 0;
            int hi = points.length;
            while (lo < hi) {
                int mid = (lo + hi) >>> 1;
                if (points[mid][0] < h) {
                    lo = mid + 1;
                } else {
                    hi = mid;
                }
            }
            return (int) points[lo == points.length ? 0 : lo][1];
        }
    }

    /** Rendezvous (highest random weight): every node scores the key; the highest score wins. O(n) per lookup. */
    record Rendezvous() implements Placement {

        @Override
        public String name() {
            return "rendezvous hashing";
        }

        @Override
        public int owner(String key, int n) {
            long h = hash(key);
            int best = 0;
            long bestScore = Long.MIN_VALUE;
            for (int node = 0; node < n; node++) {
                long score = mix(h ^ mix(node + 0x9e3779b97f4a7c15L));
                if (score > bestScore) {
                    bestScore = score;
                    best = node;
                }
            }
            return best;
        }
    }

    /** Lamping and Veach's jump consistent hash: no state, even, minimal moves; nodes can only be added at the end. */
    record Jump() implements Placement {

        @Override
        public String name() {
            return "jump consistent hash";
        }

        @Override
        public int owner(String key, int n) {
            long k = hash(key);
            long b = -1;
            long j = 0;
            while (j < n) {
                b = j;
                k = k * 2862933555777941757L + 1;
                j = (long) ((b + 1) * ((double) (1L << 31) / (double) ((k >>> 33) + 1)));
            }
            return (int) b;
        }
    }
}
