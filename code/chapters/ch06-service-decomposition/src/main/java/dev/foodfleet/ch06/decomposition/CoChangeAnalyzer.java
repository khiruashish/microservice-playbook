package dev.foodfleet.ch06.decomposition;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Evidence from version control: modules that keep changing in the same commits belong together,
 * whatever the architecture diagram says. This is the cheapest decomposition signal available,
 * and it is objective.
 *
 * <p>Coupling degree of A and B = commits touching both / commits touching the less-changed one.
 * 1.0 means "every time the smaller one changed, the other changed too".
 */
public final class CoChangeAnalyzer {

    public record Coupling(String a, String b, int coChanges, int changesA, int changesB, double degree) {
    }

    /** @param commits each element is the set of modules one commit touched */
    public List<Coupling> couplings(List<Set<String>> commits, int minCoChanges) {
        Map<String, Integer> changes = new HashMap<>();
        Map<String, Integer> pairs = new HashMap<>();
        for (Set<String> commit : commits) {
            List<String> modules = new ArrayList<>(new TreeSet<>(commit));
            modules.forEach(m -> changes.merge(m, 1, Integer::sum));
            for (int i = 0; i < modules.size(); i++) {
                for (int j = i + 1; j < modules.size(); j++) {
                    pairs.merge(modules.get(i) + "|" + modules.get(j), 1, Integer::sum);
                }
            }
        }
        List<Coupling> result = new ArrayList<>();
        pairs.forEach((key, co) -> {
            if (co >= minCoChanges) {
                String[] ab = key.split("\\|");
                int ca = changes.get(ab[0]);
                int cb = changes.get(ab[1]);
                result.add(new Coupling(ab[0], ab[1], co, ca, cb, (double) co / Math.min(ca, cb)));
            }
        });
        result.sort(Comparator.comparingDouble(Coupling::degree).reversed()
                .thenComparing(Coupling::coChanges, Comparator.reverseOrder())
                .thenComparing(Coupling::a).thenComparing(Coupling::b));
        return result;
    }

    /**
     * Groups modules whose coupling degree reaches {@code threshold} (union-find over strong pairs).
     * Every module appears in exactly one group; uncoupled modules form singleton groups.
     */
    public List<Set<String>> suggestGroups(List<Set<String>> commits, double threshold, int minCoChanges) {
        Map<String, String> parent = new TreeMap<>();
        commits.forEach(c -> c.forEach(m -> parent.putIfAbsent(m, m)));
        for (Coupling c : couplings(commits, minCoChanges)) {
            if (c.degree() >= threshold) {
                union(parent, c.a(), c.b());
            }
        }
        Map<String, Set<String>> groups = new TreeMap<>();
        parent.keySet().forEach(m -> groups.computeIfAbsent(find(parent, m), r -> new TreeSet<>()).add(m));
        List<Set<String>> result = new ArrayList<>(groups.values());
        result.sort(Comparator.comparing((Set<String> g) -> -g.size()).thenComparing(g -> g.iterator().next()));
        return result;
    }

    private static String find(Map<String, String> parent, String m) {
        String p = parent.get(m);
        if (p.equals(m)) {
            return m;
        }
        String root = find(parent, p);
        parent.put(m, root);
        return root;
    }

    private static void union(Map<String, String> parent, String a, String b) {
        String ra = find(parent, a);
        String rb = find(parent, b);
        if (!ra.equals(rb)) {
            parent.put(ra.compareTo(rb) < 0 ? rb : ra, ra.compareTo(rb) < 0 ? ra : rb);
        }
    }

    /** Parses lines like {@code c017|order,order-line,cart}. Blank lines and # comments are ignored. */
    public static List<Set<String>> parseCommitLog(List<String> lines) {
        List<Set<String>> commits = new ArrayList<>();
        for (String raw : lines) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split("\\|", 2);
            if (parts.length != 2 || parts[1].isBlank()) {
                throw new IllegalArgumentException("Malformed commit line: " + raw);
            }
            Set<String> modules = new TreeSet<>();
            for (String m : parts[1].split(",")) {
                if (!m.isBlank()) {
                    modules.add(m.strip());
                }
            }
            commits.add(modules);
        }
        return commits;
    }
}
