package dev.foodfleet.ch27.compat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A consumer of the Order API: the fields it reads, the status values and fields it was built
 * against, and how strictly its client library decodes.
 * <ul>
 *   <li>{@code strictUnknownFields}: decoding fails on fields it doesn't know (Kotlin
 *       serialization's default; Jackson 2 outside Spring Boot).</li>
 *   <li>{@code strictEnums}: an unknown enum value fails decoding (Jackson's default, including
 *       Jackson 3; exhaustive {@code when}/{@code switch} in app code).</li>
 *   <li>{@code actsOnStatuses}: status values the consumer's business logic reacts to.</li>
 *   <li>{@code hasContract}: the team publishes consumer-driven contracts the provider verifies.</li>
 * </ul>
 */
public record Consumer(String name, Map<String, Expectation> reads, List<String> knownStatuses,
                       List<String> knownFields, List<String> actsOnStatuses, boolean strictUnknownFields,
                       boolean strictEnums, boolean hasContract) {

    /** What the consumer needs from a field: its type, whether it must be present, and its meaning. */
    public record Expectation(Schema.Type type, boolean required, String meaning) {
    }

    public Consumer tolerant() {
        return new Consumer(name, reads, knownStatuses, knownFields, actsOnStatuses, false, false, hasContract);
    }

    /**
     * Ground truth: what goes wrong when this consumer decodes payloads from {@code producer} that
     * carry the given status values. Empty means it keeps working.
     */
    public List<String> problemsReading(Schema producer, List<String> statusValuesSent) {
        List<String> problems = new ArrayList<>();
        for (var e : reads.entrySet()) {
            Schema.Field f = producer.at(e.getKey());
            Expectation x = e.getValue();
            if (f == null) {
                if (x.required()) {
                    problems.add(e.getKey() + " missing");
                }
            } else if (f.type() != x.type() && !(f.type() == Schema.Type.ENUM && x.type() == Schema.Type.STRING)) {
                problems.add(e.getKey() + " is " + f.type() + ", expected " + x.type());
            } else if (!f.alwaysPresent() && x.required()) {
                problems.add(e.getKey() + " sometimes absent");
            } else if (!x.meaning().isEmpty() && !x.meaning().equals(f.meaning())) {
                problems.add(e.getKey() + " now means '" + f.meaning() + "'");
            }
        }
        if (strictEnums && reads.containsKey("status")) {
            for (String v : statusValuesSent) {
                if (!knownStatuses.contains(v)) {
                    problems.add("unknown status " + v);
                }
            }
        }
        Schema.Field status = producer.at("status");
        for (String acted : actsOnStatuses) {                 // business logic keyed on a value that is gone
            if (status != null && !status.enumValues().contains(acted)) {
                problems.add("acts on status " + acted + ", no longer sent");
            }
        }
        List<String> unknown = unknownFields(producer, "");
        if (strictUnknownFields && !unknown.isEmpty()) {
            problems.add("unknown field(s) " + unknown);
        }
        return problems;
    }

    private List<String> unknownFields(Schema schema, String prefix) {
        List<String> unknown = new ArrayList<>();
        for (var e : schema.fields().entrySet()) {
            String path = prefix + e.getKey();
            if (e.getKey().startsWith("@")) {
                continue;
            }
            if (!knownFields.contains(path)) {
                unknown.add(path);
            } else if (e.getValue().object() != null) {
                unknown.addAll(unknownFields(e.getValue().object(), path + "."));
            }
        }
        return unknown;
    }
}
