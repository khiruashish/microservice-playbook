package dev.foodfleet.ch47.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The question observability answers and monitoring can't: "what is different about the bad requests?" Given wide
 * events (any field, any cardinality) and a selection (slow, failed), compare how often each field value occurs in
 * the selection and in the rest, rank the values by that difference, then combine the top ones into a filter and
 * report how well it isolates the selection. Nothing about the failure has to be known in advance; the fields just
 * have to have been recorded.
 */
public final class BubbleUp {

    /** How often one field value occurs inside and outside the selection. */
    public record Difference(String field, String value, double inSelection, double inBaseline) {
        public double lift() {
            return inSelection - inBaseline;
        }
    }

    /** A filter of field = value pairs and how it matches the selection. */
    public record Isolation(Map<String, String> filter, int matched, int matchedSelected, int selected) {
        /** Of the events the filter matches, the share that are in the selection. */
        public double precision() {
            return matched == 0 ? 0 : (double) matchedSelected / matched;
        }

        /** Of the selected events, the share the filter matches. */
        public double recall() {
            return selected == 0 ? 0 : (double) matchedSelected / selected;
        }
    }

    private final Set<String> ignoredFields;

    /** @param ignoredFields fields that are unique per event (IDs, timestamps) or are the selection itself */
    public BubbleUp(Set<String> ignoredFields) {
        this.ignoredFields = ignoredFields;
    }

    public List<Difference> rank(List<Map<String, String>> events, Predicate<Map<String, String>> selection) {
        Map<String, int[]> counts = new HashMap<>();   // "field\0value" -> {in selection, in baseline}
        int selected = 0;
        int baseline = 0;
        for (Map<String, String> e : events) {
            boolean in = selection.test(e);
            if (in) {
                selected++;
            } else {
                baseline++;
            }
            for (Map.Entry<String, String> f : e.entrySet()) {
                if (ignoredFields.contains(f.getKey())) {
                    continue;
                }
                counts.computeIfAbsent(f.getKey() + '\0' + f.getValue(), k -> new int[2])[in ? 0 : 1]++;
            }
        }
        List<Difference> out = new ArrayList<>();
        for (Map.Entry<String, int[]> c : counts.entrySet()) {
            int split = c.getKey().indexOf('\0');
            out.add(new Difference(c.getKey().substring(0, split), c.getKey().substring(split + 1),
                    selected == 0 ? 0 : (double) c.getValue()[0] / selected,
                    baseline == 0 ? 0 : (double) c.getValue()[1] / baseline));
        }
        out.sort(Comparator.comparingDouble(Difference::lift).reversed()
                .thenComparing(Difference::field).thenComparing(Difference::value));
        return out;
    }

    /**
     * Greedily adds the highest-ranked values (one per field) to a filter while it keeps most of the selection
     * ({@code minRecall}) and raises precision; stops when precision stops improving.
     */
    public Isolation isolate(List<Map<String, String>> events, Predicate<Map<String, String>> selection,
                             int maxFields, double minRecall) {
        List<Difference> ranked = rank(events, selection);
        int selected = (int) events.stream().filter(selection).count();
        Map<String, String> filter = new LinkedHashMap<>();
        Isolation best = evaluate(events, selection, filter, selected);
        for (Difference d : ranked) {
            if (filter.size() == maxFields || d.lift() <= 0) {
                break;
            }
            if (filter.containsKey(d.field())) {
                continue;
            }
            Map<String, String> candidate = new LinkedHashMap<>(filter);
            candidate.put(d.field(), d.value());
            Isolation next = evaluate(events, selection, candidate, selected);
            if (next.recall() >= minRecall && next.precision() > best.precision()) {
                filter = candidate;
                best = next;
            }
        }
        return best;
    }

    private static Isolation evaluate(List<Map<String, String>> events, Predicate<Map<String, String>> selection,
                                      Map<String, String> filter, int selected) {
        int matched = 0;
        int matchedSelected = 0;
        for (Map<String, String> e : events) {
            boolean match = true;
            for (Map.Entry<String, String> f : filter.entrySet()) {
                if (!f.getValue().equals(e.get(f.getKey()))) {
                    match = false;
                    break;
                }
            }
            if (match) {
                matched++;
                if (selection.test(e)) {
                    matchedSelected++;
                }
            }
        }
        return new Isolation(Collections.unmodifiableMap(new LinkedHashMap<>(filter)), matched, matchedSelected, selected);
    }
}
