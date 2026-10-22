package dev.foodfleet.ch24.mini;

import java.util.List;
import java.util.Map;

/** Compact JSON for maps, lists, strings, numbers, booleans and null (sizes are measured on it). */
public final class Json {

    private Json() {
    }

    /** An object with its keys in the given order (Map.of's order changes between JVM runs). */
    public static Map<String, Object> obj(Object... keysAndValues) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        for (int k = 0; k < keysAndValues.length; k += 2) {
            m.put((String) keysAndValues[k], keysAndValues[k + 1]);
        }
        return m;
    }

    public static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        write(value, sb);
        return sb.toString();
    }

    private static void write(Object v, StringBuilder sb) {
        switch (v) {
            case null -> sb.append("null");
            case String s -> string(s, sb);
            case Number n -> sb.append(n);
            case Boolean b -> sb.append(b);
            case Map<?, ?> m -> {
                sb.append('{');
                boolean first = true;
                for (Map.Entry<?, ?> e : m.entrySet()) {
                    if (!first) {
                        sb.append(',');
                    }
                    first = false;
                    string(String.valueOf(e.getKey()), sb);
                    sb.append(':');
                    write(e.getValue(), sb);
                }
                sb.append('}');
            }
            case List<?> l -> {
                sb.append('[');
                for (int k = 0; k < l.size(); k++) {
                    if (k > 0) {
                        sb.append(',');
                    }
                    write(l.get(k), sb);
                }
                sb.append(']');
            }
            default -> string(v.toString(), sb);
        }
    }

    private static void string(String s, StringBuilder sb) {
        sb.append('"');
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                default -> sb.append(c);
            }
        }
        sb.append('"');
    }
}
