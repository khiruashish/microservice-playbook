package dev.foodfleet.ch20.detect;

import dev.foodfleet.ch20.detect.Finding.AntiPattern;
import dev.foodfleet.ch20.detect.Finding.Severity;
import dev.foodfleet.ch20.model.SystemModel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Distributed monolith: services that can't be released on their own. The evidence is the deploy
 * history: for each pair, how often they went out together relative to how often the less-deployed
 * one went out at all. Pairs above {@code threshold} are linked; linked groups of 3+ services are
 * reported as one lockstep cluster, because that is how the problem is felt: a "release train".
 */
public final class DeployCouplingDetector implements Detector {

    private final double threshold;
    private final int minimumDeploys;

    public DeployCouplingDetector(double threshold, int minimumDeploys) {
        this.threshold = threshold;
        this.minimumDeploys = minimumDeploys;
    }

    @Override
    public List<Finding> detect(SystemModel model) {
        Map<String, Integer> deploys = new HashMap<>();
        Map<String, Integer> together = new HashMap<>();
        for (SystemModel.Deploy d : model.deploys()) {
            List<String> names = new ArrayList<>(new TreeSet<>(d.services()));
            names.forEach(n -> deploys.merge(n, 1, Integer::sum));
            for (int i = 0; i < names.size(); i++) {
                for (int j = i + 1; j < names.size(); j++) {
                    together.merge(names.get(i) + "|" + names.get(j), 1, Integer::sum);
                }
            }
        }
        UnionFind clusters = new UnionFind();
        together.forEach((pair, count) -> {
            String[] ab = pair.split("\\|");
            int fewer = Math.min(deploys.get(ab[0]), deploys.get(ab[1]));
            if (fewer >= minimumDeploys && (double) count / fewer >= threshold) {
                clusters.union(ab[0], ab[1]);
            }
        });
        List<Finding> findings = new ArrayList<>();
        for (Set<String> cluster : clusters.groups()) {
            if (cluster.size() < 3) {
                continue;
            }
            long lockstep = model.deploys().stream().filter(d -> d.services().containsAll(cluster)).count();
            findings.add(new Finding(AntiPattern.DISTRIBUTED_MONOLITH, Severity.HIGH, String.join(",", cluster),
                    "%d services deployed together in %d of %d releases that touched them; pairs co-deploy >= %.0f%%"
                            .formatted(cluster.size(), lockstep, releasesTouching(model, cluster),
                                    threshold * 100),
                    "find the shared cause (schema, library, synchronous contract); version APIs and events; "
                            + "merge services that always change together"));
        }
        return findings;
    }

    private static long releasesTouching(SystemModel model, Set<String> cluster) {
        return model.deploys().stream().filter(d -> d.services().stream().anyMatch(cluster::contains)).count();
    }

    /** Minimal union-find over service names. */
    static final class UnionFind {
        private final Map<String, String> parent = new HashMap<>();

        String find(String x) {
            parent.putIfAbsent(x, x);
            String p = parent.get(x);
            if (!p.equals(x)) {
                p = find(p);
                parent.put(x, p);
            }
            return p;
        }

        void union(String a, String b) {
            parent.put(find(a), find(b));
        }

        List<Set<String>> groups() {
            Map<String, Set<String>> byRoot = new HashMap<>();
            for (String x : new ArrayList<>(parent.keySet())) {
                byRoot.computeIfAbsent(find(x), r -> new TreeSet<>()).add(x);
            }
            List<Set<String>> result = new ArrayList<>(byRoot.values());
            result.sort((a, b) -> a.iterator().next().compareTo(b.iterator().next()));
            return result;
        }
    }
}
