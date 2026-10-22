package dev.foodfleet.ch64.json;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A small JSON reader and writer: objects as LinkedHashMap, arrays as List, numbers as BigDecimal. */
public final class Json {

    private final String s;
    private int i;

    private Json(String s) {
        this.s = s;
    }

    public static Object parse(String text) {
        Json p = new Json(text);
        Object v = p.value();
        p.ws();
        if (p.i != p.s.length()) {
            throw new IllegalArgumentException("trailing data at " + p.i);
        }
        return v;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> object(Object o) {
        return (Map<String, Object>) o;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> array(Object o) {
        return (List<Object>) o;
    }

    private void ws() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
    }

    private Object value() {
        ws();
        if (i >= s.length()) {
            throw new IllegalArgumentException("unexpected end");
        }
        char c = s.charAt(i);
        switch (c) {
            case '{' -> {
                i++;
                Map<String, Object> m = new LinkedHashMap<>();
                ws();
                if (s.charAt(i) == '}') {
                    i++;
                    return m;
                }
                while (true) {
                    ws();
                    String k = string();
                    ws();
                    expect(':');
                    m.put(k, value());
                    ws();
                    if (s.charAt(i) == ',') {
                        i++;
                    } else {
                        expect('}');
                        return m;
                    }
                }
            }
            case '[' -> {
                i++;
                List<Object> l = new ArrayList<>();
                ws();
                if (s.charAt(i) == ']') {
                    i++;
                    return l;
                }
                while (true) {
                    l.add(value());
                    ws();
                    if (s.charAt(i) == ',') {
                        i++;
                    } else {
                        expect(']');
                        return l;
                    }
                }
            }
            case '"' -> {
                return string();
            }
            case 't' -> {
                literal("true");
                return Boolean.TRUE;
            }
            case 'f' -> {
                literal("false");
                return Boolean.FALSE;
            }
            case 'n' -> {
                literal("null");
                return null;
            }
            default -> {
                int start = i;
                while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) {
                    i++;
                }
                if (start == i) {
                    throw new IllegalArgumentException("unexpected '" + c + "' at " + i);
                }
                return new BigDecimal(s.substring(start, i));
            }
        }
    }

    private void literal(String word) {
        if (!s.startsWith(word, i)) {
            throw new IllegalArgumentException("expected " + word + " at " + i);
        }
        i += word.length();
    }

    private void expect(char c) {
        if (i >= s.length() || s.charAt(i) != c) {
            throw new IllegalArgumentException("expected '" + c + "' at " + i);
        }
        i++;
    }

    private String string() {
        expect('"');
        StringBuilder b = new StringBuilder();
        while (true) {
            char c = s.charAt(i++);
            if (c == '"') {
                return b.toString();
            }
            if (c == '\\') {
                char e = s.charAt(i++);
                switch (e) {
                    case 'n' -> b.append('\n');
                    case 't' -> b.append('\t');
                    case 'r' -> b.append('\r');
                    case 'b' -> b.append('\b');
                    case 'f' -> b.append('\f');
                    case 'u' -> {
                        b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                    }
                    default -> b.append(e);
                }
            } else {
                b.append(c);
            }
        }
    }

    public static String write(Object v) {
        StringBuilder b = new StringBuilder();
        write(v, b);
        return b.toString();
    }

    private static void write(Object v, StringBuilder b) {
        switch (v) {
            case null -> b.append("null");
            case String str -> {
                b.append('"');
                for (char c : str.toCharArray()) {
                    switch (c) {
                        case '"' -> b.append("\\\"");
                        case '\\' -> b.append("\\\\");
                        case '\n' -> b.append("\\n");
                        default -> b.append(c);
                    }
                }
                b.append('"');
            }
            case Map<?, ?> m -> {
                b.append('{');
                boolean first = true;
                for (Map.Entry<?, ?> e : m.entrySet()) {
                    b.append(first ? "" : ",");
                    first = false;
                    write(String.valueOf(e.getKey()), b);
                    b.append(':');
                    write(e.getValue(), b);
                }
                b.append('}');
            }
            case List<?> l -> {
                b.append('[');
                for (int k = 0; k < l.size(); k++) {
                    b.append(k == 0 ? "" : ",");
                    write(l.get(k), b);
                }
                b.append(']');
            }
            case BigDecimal d -> b.append(d.toPlainString());
            default -> b.append(v);
        }
    }
}
