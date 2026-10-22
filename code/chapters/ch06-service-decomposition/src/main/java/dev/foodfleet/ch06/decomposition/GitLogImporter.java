package dev.foodfleet.ch06.decomposition;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Turns real version-control history into the module-level commits {@link CoChangeAnalyzer} needs.
 *
 * <p>Input is the output of:
 * <pre>
 *   git log --since=12.months --no-merges --name-only --pretty=format:'#%h'
 * </pre>
 * Each commit starts with a {@code #<hash>} line followed by the paths it changed.
 *
 * <p>A mapping file assigns path prefixes to modules, one rule per line
 * ({@code src/main/java/com/foodfleet/order/=order}); the longest matching prefix wins, and paths
 * that match no rule (build files, docs) are ignored.
 *
 * <p>Commits touching more than {@code maxModulesPerCommit} modules are dropped as noise: formatting
 * runs, dependency upgrades and package renames touch everything and say nothing about coupling.
 */
public final class GitLogImporter {

    private final List<Rule> rules;
    private final int maxModulesPerCommit;

    public GitLogImporter(List<Rule> rules, int maxModulesPerCommit) {
        if (maxModulesPerCommit < 2) {
            throw new IllegalArgumentException("maxModulesPerCommit must be >= 2");
        }
        this.rules = rules.stream()
                .sorted(Comparator.comparingInt((Rule r) -> r.pathPrefix().length()).reversed())
                .toList();
        this.maxModulesPerCommit = maxModulesPerCommit;
    }

    /** Parses mapping lines of the form {@code path/prefix/=module}. Blank lines and # comments are ignored. */
    public static List<Rule> parseRules(List<String> lines) {
        List<Rule> rules = new ArrayList<>();
        for (String raw : lines) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int eq = line.lastIndexOf('=');
            if (eq <= 0 || eq == line.length() - 1) {
                throw new IllegalArgumentException("Malformed mapping rule (expected prefix=module): " + raw);
            }
            rules.add(new Rule(line.substring(0, eq).strip(), line.substring(eq + 1).strip()));
        }
        if (rules.isEmpty()) {
            throw new IllegalArgumentException("Mapping file contains no rules");
        }
        return rules;
    }

    public Optional<String> moduleOf(String path) {
        return rules.stream().filter(r -> path.startsWith(r.pathPrefix())).map(Rule::module).findFirst();
    }

    public Result importLog(List<String> gitLogLines) {
        List<Set<String>> commits = new ArrayList<>();
        int seen = 0;
        int noisy = 0;
        int unmapped = 0;
        Set<String> current = null;
        for (String raw : gitLogLines) {
            String line = raw.strip();
            if (line.startsWith("#")) {
                if (current != null) {
                    int outcome = accept(current, commits);
                    noisy += outcome == NOISY ? 1 : 0;
                    unmapped += outcome == UNMAPPED ? 1 : 0;
                }
                current = new TreeSet<>();
                seen++;
            } else if (!line.isEmpty() && current != null) {
                moduleOf(line).ifPresent(current::add);
            }
        }
        if (current != null) {
            int outcome = accept(current, commits);
            noisy += outcome == NOISY ? 1 : 0;
            unmapped += outcome == UNMAPPED ? 1 : 0;
        }
        return new Result(commits, seen, noisy, unmapped);
    }

    private static final int KEPT = 0;
    private static final int NOISY = 1;
    private static final int UNMAPPED = 2;

    private int accept(Set<String> modules, List<Set<String>> commits) {
        if (modules.isEmpty()) {
            return UNMAPPED;
        }
        if (modules.size() > maxModulesPerCommit) {
            return NOISY;
        }
        commits.add(Set.copyOf(modules));
        return KEPT;
    }

    public record Rule(String pathPrefix, String module) {
    }

    /**
     * @param commits      module sets of the commits kept for analysis
     * @param commitsSeen  commits found in the log
     * @param droppedNoisy commits that touched too many modules to be meaningful
     * @param droppedUnmapped commits that touched no mapped module (docs, build files)
     */
    public record Result(List<Set<String>> commits, int commitsSeen, int droppedNoisy, int droppedUnmapped) {
    }
}
