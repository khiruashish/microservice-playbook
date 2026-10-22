package dev.foodfleet.ch16.strangler;

import dev.foodfleet.ch16.pricing.PriceQuote;
import dev.foodfleet.ch16.pricing.PriceRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Compares the new service's answer with the monolith's, field by field, and keeps counts and a few
 * examples per kind of difference. Differences within {@code tolerancePaise} on surcharge and total
 * (and nowhere else) are counted separately as accepted rounding differences.
 */
public final class ShadowComparator {

    public enum Outcome { MATCH, WITHIN_TOLERANCE, MISMATCH, NEW_FAILED }

    public record Example(PriceRequest request, PriceQuote legacy, PriceQuote candidate) {
    }

    private final long tolerancePaise;
    private final Map<Outcome, Integer> outcomes = new TreeMap<>();
    private final Map<String, Integer> mismatchesByField = new TreeMap<>();
    private final Map<String, List<Example>> examples = new TreeMap<>();

    public ShadowComparator(long tolerancePaise) {
        this.tolerancePaise = tolerancePaise;
    }

    public Outcome compare(PriceRequest request, PriceQuote legacy, PriceQuote candidate) {
        Outcome outcome;
        if (legacy.equals(candidate)) {
            outcome = Outcome.MATCH;
        } else if (legacy.itemsPaise() == candidate.itemsPaise()
                && legacy.deliveryFeePaise() == candidate.deliveryFeePaise()
                && Math.abs(legacy.surchargePaise() - candidate.surchargePaise()) <= tolerancePaise
                && Math.abs(legacy.totalPaise() - candidate.totalPaise()) <= tolerancePaise) {
            outcome = Outcome.WITHIN_TOLERANCE;
        } else {
            outcome = Outcome.MISMATCH;
            String fields = differingFields(legacy, candidate);
            mismatchesByField.merge(fields, 1, Integer::sum);
            List<Example> list = examples.computeIfAbsent(fields, f -> new ArrayList<>());
            if (list.size() < 3) {
                list.add(new Example(request, legacy, candidate));
            }
        }
        outcomes.merge(outcome, 1, Integer::sum);
        return outcome;
    }

    public void newServiceFailed() {
        outcomes.merge(Outcome.NEW_FAILED, 1, Integer::sum);
    }

    private static String differingFields(PriceQuote a, PriceQuote b) {
        List<String> fields = new ArrayList<>();
        if (a.itemsPaise() != b.itemsPaise()) {
            fields.add("items");
        }
        if (a.deliveryFeePaise() != b.deliveryFeePaise()) {
            fields.add("deliveryFee");
        }
        if (a.surchargePaise() != b.surchargePaise()) {
            fields.add("surcharge");
        }
        return String.join("+", fields);
    }

    public int count(Outcome outcome) {
        return outcomes.getOrDefault(outcome, 0);
    }

    public int total() {
        return outcomes.values().stream().mapToInt(Integer::intValue).sum();
    }

    public Map<String, Integer> mismatchesByField() {
        return Collections.unmodifiableMap(new TreeMap<>(mismatchesByField));
    }

    public Map<String, List<Example>> examples() {
        return Map.copyOf(examples);
    }
}
