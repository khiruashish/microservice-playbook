package dev.foodfleet.ch20.detect;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Findings the team has looked at and accepted for now, each with an owner and a ticket. The
 * fitness function fails the build for any finding NOT in the baseline (a new anti-pattern), and
 * for any baseline entry that is no longer found (fixed: delete it, so it can't come back quietly)
 * or whose optional {@code until} date has passed. The baseline can only shrink: a ratchet.
 *
 * <pre>
 * SHARED_DATABASE:customers  owner=growth ticket=FF-1412 until=2025-03-31
 * </pre>
 */
public final class Baseline {

    public record Entry(String key, Map<String, String> attributes) {
        Optional<LocalDate> until() {
            return Optional.ofNullable(attributes.get("until")).map(LocalDate::parse);
        }
    }

    private final Map<String, Entry> entries;

    private Baseline(Map<String, Entry> entries) {
        this.entries = entries;
    }

    public static Baseline parse(String text) {
        Map<String, Entry> entries = new LinkedHashMap<>();
        for (String raw : text.split("\\R")) {
            String line = raw.replaceAll("#.*", "").strip();
            if (line.isEmpty()) {
                continue;
            }
            String[] words = line.split("\\s+");
            Map<String, String> attributes = new LinkedHashMap<>();
            for (int i = 1; i < words.length; i++) {
                String[] kv = words[i].split("=", 2);
                attributes.put(kv[0], kv.length > 1 ? kv[1] : "");
            }
            if (!attributes.containsKey("owner") || !attributes.containsKey("ticket")) {
                throw new IllegalArgumentException("baseline entry needs owner= and ticket=: " + raw);
            }
            entries.put(words[0], new Entry(words[0], attributes));
        }
        return new Baseline(entries);
    }

    /** Everything that should fail the build, as human-readable lines. Empty means pass. */
    public List<String> violations(List<Finding> findings, LocalDate today) {
        List<String> problems = new ArrayList<>();
        for (Finding f : findings) {
            Entry accepted = entries.get(f.key());
            if (accepted == null) {
                problems.add("NEW " + f.key() + " (" + f.severity() + "): " + f.evidence());
            } else if (accepted.until().filter(today::isAfter).isPresent()) {
                problems.add("EXPIRED " + f.key() + " was accepted until " + accepted.until().get() + " ("
                        + accepted.attributes().get("ticket") + ")");
            }
        }
        for (String key : entries.keySet()) {
            if (findings.stream().noneMatch(f -> f.key().equals(key))) {
                problems.add("FIXED " + key + ": remove it from the baseline so it can't return unnoticed");
            }
        }
        return problems;
    }
}
