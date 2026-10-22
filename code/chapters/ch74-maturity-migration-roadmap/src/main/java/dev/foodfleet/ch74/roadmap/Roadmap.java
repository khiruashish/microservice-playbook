package dev.foodfleet.ch74.roadmap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * Extraction roadmaps over a codebase and its history. After each step, the extracted contexts are services, the
 * rest is the monolith, and the platform is a library linked into all of them. For every step it measures what the
 * split costs and what it buys.
 */
public final class Roadmap {

    public static final String MONOLITH = "monolith";

    /**
     * @param remote        import edges between different deployables: calls and types that must cross a network or a contract
     * @param inversions    import edges from the shared platform library into an extracted service (the library can't depend on a service)
     * @param bidirectional pairs of deployables that depend on each other both ways
     * @param coordinated   commits that changed two or more deployables (counting the platform library as one)
     * @param independent   commits that changed exactly one extracted service and nothing else
     */
    public record Cost(int remote, int inversions, int bidirectional, int coordinated, int independent) {
    }

    public record Step(String extracted, Cost cost) {
    }

    private final Codebase code;
    private final History history;
    private final Contexts contexts;

    public Roadmap(Codebase code, History history, Contexts contexts) {
        this.code = code;
        this.history = history;
        this.contexts = contexts;
    }

    static String deployable(String context, Set<String> extracted) {
        if (extracted.contains(context)) {
            return context;
        }
        return Contexts.PLATFORM.equals(context) ? Contexts.PLATFORM : MONOLITH;
    }

    public Cost cost(Set<String> extracted) {
        int remote = 0;
        int inversions = 0;
        Set<String> directed = new TreeSet<>();
        for (var e : code.edges.entrySet()) {
            String[] ab = e.getKey().split(">");
            String from = deployable(ab[0], extracted);
            String to = deployable(ab[1], extracted);
            if (to.equals(Contexts.PLATFORM) || from.equals(to)) {
                continue;
            }
            if (from.equals(Contexts.PLATFORM)) {
                if (!to.equals(MONOLITH)) {
                    inversions += e.getValue();
                }
                continue;
            }
            remote += e.getValue();
            directed.add(from + ">" + to);
        }
        int bidirectional = 0;
        for (String d : directed) {
            String[] ab = d.split(">");
            if (ab[0].compareTo(ab[1]) < 0 && directed.contains(ab[1] + ">" + ab[0])) {
                bidirectional++;
            }
        }
        int coordinated = 0;
        int independent = 0;
        for (Set<String> commit : history.commits) {
            Set<String> d = new TreeSet<>();
            commit.forEach(c -> d.add(deployable(c, extracted)));
            if (d.size() > 1) {
                coordinated++;
            } else if (!d.contains(MONOLITH) && !d.contains(Contexts.PLATFORM)) {
                independent++;
            }
        }
        return new Cost(remote, inversions, bidirectional, coordinated, independent);
    }

    /** Extracts the candidates in a fixed order. */
    public List<Step> plan(List<String> order) {
        List<Step> steps = new ArrayList<>();
        Set<String> extracted = new LinkedHashSet<>();
        for (String c : order) {
            extracted.add(c);
            steps.add(new Step(c, cost(extracted)));
        }
        return steps;
    }

    /** At each step, extracts the candidate that leaves the fewest remote edges (then the fewest coordinated commits). */
    public List<Step> greedy() {
        List<Step> steps = new ArrayList<>();
        Set<String> extracted = new LinkedHashSet<>();
        Set<String> left = new LinkedHashSet<>(contexts.candidates);
        while (!left.isEmpty()) {
            String best = null;
            Cost bestCost = null;
            for (String c : left) {
                Set<String> next = new LinkedHashSet<>(extracted);
                next.add(c);
                Cost k = cost(next);
                if (bestCost == null || k.remote() < bestCost.remote() || k.remote() == bestCost.remote() && k.coordinated() < bestCost.coordinated()) {
                    best = c;
                    bestCost = k;
                }
            }
            extracted.add(best);
            left.remove(best);
            steps.add(new Step(best, bestCost));
        }
        return steps;
    }

    /**
     * At each step, extracts the candidate that buys the most independent changes per remote edge it adds (value per
     * cost); a candidate that adds no remote edges and buys something comes first.
     */
    public List<Step> valuePerCost() {
        List<Step> steps = new ArrayList<>();
        Set<String> extracted = new LinkedHashSet<>();
        Set<String> left = new LinkedHashSet<>(contexts.candidates);
        Cost now = cost(extracted);
        while (!left.isEmpty()) {
            String best = null;
            Cost bestCost = null;
            double bestScore = -1;
            for (String c : left) {
                Set<String> next = new LinkedHashSet<>(extracted);
                next.add(c);
                Cost k = cost(next);
                double score = (k.independent() - now.independent()) / (double) (Math.max(0, k.remote() - now.remote()) + 1);
                if (score > bestScore) {
                    best = c;
                    bestCost = k;
                    bestScore = score;
                }
            }
            extracted.add(best);
            left.remove(best);
            steps.add(new Step(best, bestCost));
            now = bestCost;
        }
        return steps;
    }

    /** A layering of contexts and the import edges that point against it. */
    public record Layering(List<String> order, int against, List<String> edges) {
    }

    /**
     * The order of contexts (lowest layer first) that leaves the fewest import edges pointing upwards: a minimum
     * weighted feedback arc set, solved exactly by dynamic programming over subsets (fine for up to about 20 contexts).
     * Those edges are what must be removed or inverted before the contexts form layers with no cycles.
     */
    public Layering layering(boolean withPlatform) {
        List<String> nodes = new ArrayList<>();
        for (String c : contexts.all) {
            if (withPlatform || !c.equals(Contexts.PLATFORM)) {
                nodes.add(c);
            }
        }
        int n = nodes.size();
        int[][] w = new int[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                w[i][j] = i == j ? 0 : code.edges(nodes.get(i), nodes.get(j));
            }
        }
        // best[S]: fewest upward edges when the contexts in S are placed below all others; a context placed next sits
        // above S, so its dependants already in S point upwards at it
        int[] best = new int[1 << n];
        int[] choice = new int[1 << n];
        java.util.Arrays.fill(best, Integer.MAX_VALUE);
        best[0] = 0;
        for (int set = 0; set < (1 << n); set++) {
            if (best[set] == Integer.MAX_VALUE) {
                continue;
            }
            for (int v = 0; v < n; v++) {
                if ((set & (1 << v)) != 0) {
                    continue;
                }
                int up = 0;
                for (int u = 0; u < n; u++) {
                    if ((set & (1 << u)) != 0) {
                        up += w[u][v];
                    }
                }
                int next = set | (1 << v);
                if (best[set] + up < best[next]) {
                    best[next] = best[set] + up;
                    choice[next] = v;
                }
            }
        }
        List<String> order = new ArrayList<>();
        for (int set = (1 << n) - 1; set != 0; set &= ~(1 << choice[set])) {
            order.addFirst(nodes.get(choice[set]));
        }
        List<String> against = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                int k = code.edges(order.get(i), order.get(j));
                if (k > 0) {
                    against.add(order.get(i) + ">" + order.get(j) + " " + k);
                }
            }
        }
        return new Layering(order, best[(1 << n) - 1], against);
    }

    /** Candidates sorted by a key, largest first, ties by name. */
    public List<String> order(Function<String, Integer> key) {
        return contexts.candidates.stream().sorted(Comparator.comparing(key).reversed().thenComparing(Comparator.naturalOrder())).toList();
    }

    public int inbound(String context) {
        int n = 0;
        for (var e : code.edges.entrySet()) {
            if (e.getKey().endsWith(">" + context)) {
                n += e.getValue();
            }
        }
        return n;
    }

    /** Strongly connected groups of contexts (Tarjan), of two or more, over import edges; optionally without the platform. */
    public List<Set<String>> cycles(boolean withPlatform) {
        Map<String, List<String>> g = new LinkedHashMap<>();
        for (String c : contexts.all) {
            if (withPlatform || !c.equals(Contexts.PLATFORM)) {
                g.put(c, new ArrayList<>());
            }
        }
        for (String e : code.edges.keySet()) {
            String[] ab = e.split(">");
            if (g.containsKey(ab[0]) && g.containsKey(ab[1])) {
                g.get(ab[0]).add(ab[1]);
            }
        }
        List<Set<String>> out = new ArrayList<>();
        Map<String, Integer> index = new HashMap<>();
        Map<String, Integer> low = new HashMap<>();
        List<String> stack = new ArrayList<>();
        Set<String> on = new TreeSet<>();
        int[] counter = {0};
        for (String v : g.keySet()) {
            if (!index.containsKey(v)) {
                tarjan(v, g, index, low, stack, on, counter, out);
            }
        }
        return out;
    }

    private static void tarjan(String v, Map<String, List<String>> g, Map<String, Integer> index, Map<String, Integer> low, List<String> stack,
                               Set<String> on, int[] counter, List<Set<String>> out) {
        index.put(v, counter[0]);
        low.put(v, counter[0]++);
        stack.add(v);
        on.add(v);
        for (String w : g.get(v)) {
            if (!index.containsKey(w)) {
                tarjan(w, g, index, low, stack, on, counter, out);
                low.put(v, Math.min(low.get(v), low.get(w)));
            } else if (on.contains(w)) {
                low.put(v, Math.min(low.get(v), index.get(w)));
            }
        }
        if (low.get(v).equals(index.get(v))) {
            Set<String> scc = new TreeSet<>();
            String w;
            do {
                w = stack.removeLast();
                on.remove(w);
                scc.add(w);
            } while (!w.equals(v));
            if (scc.size() > 1) {
                out.add(scc);
            }
        }
    }
}
