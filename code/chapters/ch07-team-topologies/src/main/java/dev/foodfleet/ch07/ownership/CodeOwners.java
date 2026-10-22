package dev.foodfleet.ch07.ownership;

import dev.foodfleet.ch07.topology.Topology;
import dev.foodfleet.ch07.topology.Topology.Component;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Keeps GitHub's CODEOWNERS file and the team topology in agreement: generate it from the
 * topology, or check an existing one against it in CI.
 *
 * <p>Matching follows GitHub's rule that the <b>last</b> matching line wins. This checker supports
 * the two pattern forms ownership files mostly use for components, {@code *} and anchored
 * directories ({@code /services/ordering/}); other patterns are reported as skipped rather than
 * guessed at.
 */
public final class CodeOwners {

    public record Rule(int line, String pattern, List<String> owners) {
        boolean matches(String path) {
            return pattern.equals("*") || ("/" + path).startsWith(pattern);
        }
    }

    public record Parsed(List<Rule> rules, List<String> skipped) {
    }

    public record Finding(String component, String path, String problem) {
    }

    private final String organisation;

    public CodeOwners(String organisation) {
        this.organisation = organisation;
    }

    /** {@code Restaurant Partner} → {@code @foodfleet/restaurant-partner}. */
    public String handle(String team) {
        return "@" + organisation + "/" + team.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
    }

    public String generate(Topology topology) {
        StringBuilder out = new StringBuilder();
        out.append("# Generated from the team topology (").append(topology.name()).append("). Do not edit by hand.\n");
        out.append("# Exactly one owning team per component; the last matching line wins.\n");
        topology.components().stream()
                .sorted((x, y) -> x.path().compareTo(y.path()))
                .forEach(c -> out.append(String.format(Locale.ROOT, "%-34s %s%n", "/" + c.path(), handle(c.owner()))));
        return out.toString();
    }

    public static Parsed parse(List<String> lines) {
        List<Rule> rules = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split("\\s+");
            String pattern = parts[0];
            if (!pattern.equals("*") && !(pattern.startsWith("/") && pattern.endsWith("/") && !pattern.contains("*"))) {
                skipped.add("line " + (i + 1) + ": " + pattern);
                continue;
            }
            rules.add(new Rule(i + 1, pattern, List.copyOf(Arrays.asList(parts).subList(1, parts.length))));
        }
        return new Parsed(rules, skipped);
    }

    /** Last matching rule wins, as on GitHub. */
    public static Optional<Rule> effectiveRule(List<Rule> rules, String path) {
        Rule match = null;
        for (Rule r : rules) {
            if (r.matches(path)) {
                match = r;
            }
        }
        return Optional.ofNullable(match);
    }

    public List<Finding> check(Topology topology, Parsed parsed) {
        List<Finding> findings = new ArrayList<>();
        for (Component c : topology.components()) {
            Optional<Rule> rule = effectiveRule(parsed.rules(), c.path());
            String expected = handle(c.owner());
            if (rule.isEmpty() || rule.get().owners().isEmpty()) {
                findings.add(new Finding(c.name(), c.path(), "no owner"));
            } else if (rule.get().owners().size() > 1) {
                findings.add(new Finding(c.name(), c.path(),
                        "shared by " + String.join(" ", rule.get().owners()) + " (line " + rule.get().line() + "): expected only " + expected));
            } else if (!rule.get().owners().getFirst().equals(expected)) {
                findings.add(new Finding(c.name(), c.path(),
                        "owned by " + rule.get().owners().getFirst() + " (line " + rule.get().line() + "), topology says " + expected));
            }
        }
        return findings;
    }
}
