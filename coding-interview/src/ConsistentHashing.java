import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Problem: route keys (user ids) to cache/DB shards so that adding or removing a node moves only ~1/N of the keys. */
public class ConsistentHashing {

    static final class Ring {
        private final TreeMap<Long, String> ring = new TreeMap<>();
        private final int virtualNodes;

        Ring(int virtualNodes) { this.virtualNodes = virtualNodes; }

        void addNode(String node) { for (int v = 0; v < virtualNodes; v++) ring.put(hash(node + "#" + v), node); }
        void removeNode(String node) { for (int v = 0; v < virtualNodes; v++) ring.remove(hash(node + "#" + v)); }

        /** First virtual node clockwise from the key's position; wrap around to the start of the ring. */
        String nodeFor(String key) {
            if (ring.isEmpty()) throw new IllegalStateException("empty ring");
            Map.Entry<Long, String> e = ring.ceilingEntry(hash(key));
            return (e != null ? e : ring.firstEntry()).getValue();
        }

        /** Replication: the next N DISTINCT physical nodes clockwise. */
        List<String> replicasFor(String key, int n) {
            LinkedHashSet<String> out = new LinkedHashSet<>();
            for (String node : ring.tailMap(hash(key)).values()) { out.add(node); if (out.size() == n) return List.copyOf(out); }
            for (String node : ring.values()) { out.add(node); if (out.size() == n) break; }
            return List.copyOf(out);
        }

        static long hash(String s) {
            try {
                byte[] d = MessageDigest.getInstance("MD5").digest(s.getBytes(StandardCharsets.UTF_8));
                long h = 0;
                for (int i = 0; i < 8; i++) h = (h << 8) | (d[i] & 0xff);   // first 64 bits: well distributed
                return h;
            } catch (Exception e) { throw new IllegalStateException(e); }
        }
    }

    static Map<String, String> assign(Ring ring, List<String> keys) {
        Map<String, String> m = new HashMap<>();
        for (String k : keys) m.put(k, ring.nodeFor(k));
        return m;
    }

    public static void main(String[] args) {
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < 10_000; i++) keys.add("user-" + i);

        Ring ring = new Ring(150);
        for (String n : List.of("cache-1", "cache-2", "cache-3", "cache-4")) ring.addNode(n);
        Map<String, String> before = assign(ring, keys);
        Map<String, Integer> load = new TreeMap<>();
        before.values().forEach(n -> load.merge(n, 1, Integer::sum));
        System.out.println("load with 150 vnodes/node: " + load);

        ring.addNode("cache-5");
        Map<String, String> after = assign(ring, keys);
        long moved = keys.stream().filter(k -> !before.get(k).equals(after.get(k))).count();
        long movedToNew = keys.stream().filter(k -> !before.get(k).equals(after.get(k)) && after.get(k).equals("cache-5")).count();
        System.out.printf("add cache-5: moved %d of 10000 keys (%.1f%%; ideal 1/5 = 20%%), all moves went to cache-5: %s%n",
                moved, moved / 100.0, moved == movedToNew);

        long naiveMoved = keys.stream().filter(k -> Math.floorMod(k.hashCode(), 4) != Math.floorMod(k.hashCode(), 5)).count();
        System.out.printf("naive hash %% N from 4 to 5 nodes would move %.1f%% of keys%n", naiveMoved / 100.0);
        System.out.println("replicas for user-42 = " + ring.replicasFor("user-42", 3));

        check(moved == movedToNew, "only keys that now belong to the new node move");
        check(moved > 1_500 && moved < 2_500, "about 1/N of keys move");
        check(load.values().stream().allMatch(c -> c > 2_000 && c < 3_000), "virtual nodes balance load");
        check(new HashSet<>(ring.replicasFor("user-42", 3)).size() == 3, "replicas on distinct nodes");
        System.out.println("PASS");
    }

    static void check(boolean ok, String what) { if (!ok) { System.out.println("FAILED: " + what); System.exit(1); } }
}
