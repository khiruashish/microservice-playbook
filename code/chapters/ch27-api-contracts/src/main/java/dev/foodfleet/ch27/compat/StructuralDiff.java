package dev.foodfleet.ch27.compat;

import java.util.ArrayList;
import java.util.List;

/**
 * What a structural diff of two response schemas reports (the checks tools such as oasdiff run on
 * OpenAPI documents, simplified). ERROR fails the build; WARN is reported. Changes of meaning are
 * not in the schema, so they are not seen.
 */
public final class StructuralDiff {

    public enum Severity { NONE, WARN, ERROR }

    public record Finding(Severity severity, String message) {
    }

    private StructuralDiff() {
    }

    public static List<Finding> compare(Schema before, Schema after) {
        List<Finding> findings = new ArrayList<>();
        compare(before, after, "", findings);
        return findings;
    }

    public static Severity worst(List<Finding> findings) {
        return findings.stream().map(Finding::severity).max(Enum::compareTo).orElse(Severity.NONE);
    }

    private static void compare(Schema before, Schema after, String prefix, List<Finding> out) {
        for (var e : before.fields().entrySet()) {
            if (e.getKey().startsWith("@")) {
                continue;                                  // behaviour is not in the schema document
            }
            String path = prefix + e.getKey();
            Schema.Field old = e.getValue();
            Schema.Field now = after.fields().get(e.getKey());
            if (now == null) {
                out.add(new Finding(Severity.ERROR, "response property removed: " + path));
                continue;
            }
            if (old.type() != now.type()) {
                out.add(new Finding(Severity.ERROR, "response property type changed: " + path));
            }
            if (old.alwaysPresent() && !now.alwaysPresent()) {
                out.add(new Finding(Severity.ERROR, "response property became optional: " + path));
            }
            for (String v : old.enumValues()) {
                if (!now.enumValues().contains(v)) {
                    out.add(new Finding(Severity.ERROR, "response enum value removed: " + path + "=" + v));
                }
            }
            for (String v : now.enumValues()) {
                if (!old.enumValues().contains(v)) {
                    out.add(new Finding(Severity.WARN, "response enum value added: " + path + "=" + v));
                }
            }
            if (old.object() != null && now.object() != null) {
                compare(old.object(), now.object(), path + ".", out);
            }
        }
    }
}
