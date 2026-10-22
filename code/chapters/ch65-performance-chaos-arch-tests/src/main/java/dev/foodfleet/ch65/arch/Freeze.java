package dev.foodfleet.ch65.arch;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * ArchUnit's FreezingArchRule, in miniature: record a rule's violations once (the store), then report only
 * violations that aren't in it. Existing debt is accepted; new debt fails the build.
 */
public final class Freeze {

    private Freeze() {
    }

    public static Map<String, Set<String>> store(Map<String, List<String>> violations) {
        Map<String, Set<String>> s = new LinkedHashMap<>();
        violations.forEach((rule, v) -> s.put(rule, new TreeSet<>(v)));
        return s;
    }

    public static Map<String, List<String>> newOnly(Map<String, List<String>> violations, Map<String, Set<String>> store) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        violations.forEach((rule, v) -> {
            List<String> fresh = new ArrayList<>();
            for (String line : v) {
                if (!store.getOrDefault(rule, Set.of()).contains(line)) {
                    fresh.add(line);
                }
            }
            out.put(rule, fresh);
        });
        return out;
    }
}
