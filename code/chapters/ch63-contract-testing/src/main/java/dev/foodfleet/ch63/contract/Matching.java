package dev.foodfleet.ch63.contract;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Pact's response-body matching, for the rules these contracts use:
 * <ul>
 *   <li>every key the consumer expects must be present; keys it doesn't mention are allowed (Postel's law)</li>
 *   <li>{@code type}: same JSON type as the example, cascading to everything below it; with {@code min} on an array,
 *       at least that many elements, each matching the first example</li>
 *   <li>{@code regex}: the value, as a string, matches the pattern</li>
 *   <li>no rule: equal to the example</li>
 * </ul>
 */
public final class Matching {

    private Matching() {
    }

    public static List<String> match(Object expected, Object actual, Map<String, Map<String, Object>> rules) {
        List<String> out = new ArrayList<>();
        match(expected, actual, "$", rules, out);
        return out;
    }

    static void match(Object expected, Object actual, String path, Map<String, Map<String, Object>> rules, List<String> out) {
        Map<String, Object> rule = ruleFor(path, rules);
        String kind = rule == null ? "equality" : (String) rule.get("match");
        if (expected instanceof Map<?, ?> e) {
            if (!(actual instanceof Map<?, ?> a)) {
                out.add(path + ": expected an object, got " + describe(actual));
                return;
            }
            for (Map.Entry<?, ?> entry : e.entrySet()) {
                String child = path + "." + entry.getKey();
                if (!a.containsKey(entry.getKey())) {
                    out.add(child + ": missing");
                } else {
                    match(entry.getValue(), a.get(entry.getKey()), child, rules, out);
                }
            }
            return;
        }
        if (expected instanceof List<?> e) {
            if (!(actual instanceof List<?> a)) {
                out.add(path + ": expected an array, got " + describe(actual));
                return;
            }
            if (kind.equals("type") && rule.get("min") != null) {
                int min = ((Number) rule.get("min")).intValue();
                if (a.size() < min) {
                    out.add(path + ": expected at least " + min + " elements, got " + a.size());
                }
                for (int k = 0; k < a.size(); k++) {
                    match(e.getFirst(), a.get(k), path + "[" + k + "]", rules, out);
                }
            } else if (a.size() != e.size()) {
                out.add(path + ": expected " + e.size() + " elements, got " + a.size());
            } else {
                for (int k = 0; k < a.size(); k++) {
                    match(e.get(k), a.get(k), path + "[" + k + "]", rules, out);
                }
            }
            return;
        }
        switch (kind) {
            case "regex" -> {
                String regex = (String) rule.get("regex");
                if (actual == null || actual instanceof Map || actual instanceof List || !String.valueOf(actual instanceof BigDecimal d ? d.toPlainString() : actual).matches(regex)) {
                    out.add(path + ": " + describe(actual) + " doesn't match /" + regex + "/");
                }
            }
            case "type" -> {
                if (!type(expected).equals(type(actual))) {
                    out.add(path + ": expected a " + type(expected) + ", got " + describe(actual));
                }
            }
            default -> {
                boolean equal = expected instanceof BigDecimal x && actual instanceof BigDecimal y ? x.compareTo(y) == 0
                        : expected == null ? actual == null : expected.equals(actual);
                if (!equal) {
                    out.add(path + ": expected " + describe(expected) + ", got " + describe(actual));
                }
            }
        }
    }

    /** The rule for a path: an exact key (with [*] for any index), else a type rule on an ancestor, which cascades. */
    static Map<String, Object> ruleFor(String path, Map<String, Map<String, Object>> rules) {
        String generic = path.replaceAll("\\[\\d+]", "[*]");
        if (rules.containsKey(generic)) {
            return rules.get(generic);
        }
        String p = generic;
        while (p.contains(".") || p.endsWith("]")) {
            p = p.endsWith("]") ? p.substring(0, p.lastIndexOf('[')) : p.substring(0, p.lastIndexOf('.'));
            Map<String, Object> r = rules.get(p);
            if (r != null && "type".equals(r.get("match"))) {
                return Map.of("match", "type");
            }
        }
        return null;
    }

    static String type(Object v) {
        return switch (v) {
            case null -> "null";
            case String s -> "string";
            case BigDecimal d -> "number";
            case Boolean b -> "boolean";
            case Map<?, ?> m -> "object";
            case List<?> l -> "array";
            default -> v.getClass().getSimpleName();
        };
    }

    static String describe(Object v) {
        return v instanceof String s ? "\"" + s + "\"" : v == null ? "null" : type(v) + " " + v;
    }
}
