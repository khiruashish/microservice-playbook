package dev.foodfleet.ch41.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON for SBOMs and OSV advisories, so the scanner needs only the JDK (the same parser as chapter 36).
 * Objects become {@code Map<String,Object>}, arrays {@code List<Object>}, numbers {@code Long} or {@code Double}.
 * Strict: trailing data, duplicate keys and bad escapes are errors (a lenient parser is an attack surface).
 */
public final class Json {

    public static final class ParseException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;
        ParseException(String message) {
            super(message);
        }
    }

    private final String s;
    private int i;

    private Json(String s) {
        this.s = s;
    }

    public static Object parse(String text) {
        Json p = new Json(text);
        p.ws();
        Object v = p.value(0);
        p.ws();
        if (p.i != text.length()) {
            throw new ParseException("trailing data at " + p.i);
        }
        return v;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> object(String text) {
        Object v = parse(text);
        if (!(v instanceof Map)) {
            throw new ParseException("not a JSON object");
        }
        return (Map<String, Object>) v;
    }

    private Object value(int depth) {
        if (depth > 32) {
            throw new ParseException("nested too deeply");
        }
        if (i >= s.length()) {
            throw new ParseException("unexpected end");
        }
        char c = s.charAt(i);
        switch (c) {
            case '{' -> {
                i++;
                Map<String, Object> m = new LinkedHashMap<>();
                ws();
                if (peek('}')) {
                    i++;
                    return m;
                }
                while (true) {
                    ws();
                    if (!peek('"')) {
                        throw new ParseException("expected a key at " + i);
                    }
                    String k = string();
                    ws();
                    expect(':');
                    ws();
                    if (m.containsKey(k)) {
                        throw new ParseException("duplicate key " + k);    // which value would a verifier use?
                    }
                    m.put(k, value(depth + 1));
                    ws();
                    if (peek(',')) {
                        i++;
                        continue;
                    }
                    expect('}');
                    return m;
                }
            }
            case '[' -> {
                i++;
                List<Object> l = new ArrayList<>();
                ws();
                if (peek(']')) {
                    i++;
                    return l;
                }
                while (true) {
                    ws();
                    l.add(value(depth + 1));
                    ws();
                    if (peek(',')) {
                        i++;
                        continue;
                    }
                    expect(']');
                    return l;
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
                return number();
            }
        }
    }

    private String string() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (i >= s.length()) {
                throw new ParseException("unterminated string");
            }
            char c = s.charAt(i++);
            if (c == '"') {
                return sb.toString();
            }
            if (c < 0x20) {
                throw new ParseException("control character in string");
            }
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            char e = s.charAt(i++);
            switch (e) {
                case '"', '\\', '/' -> sb.append(e);
                case 'b' -> sb.append('\b');
                case 'f' -> sb.append('\f');
                case 'n' -> sb.append('\n');
                case 'r' -> sb.append('\r');
                case 't' -> sb.append('\t');
                case 'u' -> {
                    sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                    i += 4;
                }
                default -> throw new ParseException("bad escape \\" + e);
            }
        }
    }

    private Object number() {
        int start = i;
        while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) {
            i++;
        }
        String n = s.substring(start, i);
        if (n.isEmpty()) {
            throw new ParseException("unexpected character at " + start);
        }
        try {
            return n.contains(".") || n.contains("e") || n.contains("E") ? (Object) Double.parseDouble(n)
                    : (Object) Long.parseLong(n);
        } catch (NumberFormatException e) {
            throw new ParseException("bad number " + n);
        }
    }

    private void literal(String word) {
        if (!s.startsWith(word, i)) {
            throw new ParseException("unexpected token at " + i);
        }
        i += word.length();
    }

    private boolean peek(char c) {
        return i < s.length() && s.charAt(i) == c;
    }

    private void expect(char c) {
        if (!peek(c)) {
            throw new ParseException("expected '" + c + "' at " + i);
        }
        i++;
    }

    private void ws() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
    }

    public static String write(Object v) {
        StringBuilder sb = new StringBuilder();
        write(sb, v);
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object v) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof String str) {
            sb.append('"');
            for (char c : str.toCharArray()) {
                switch (c) {
                    case '"' -> sb.append("\\\"");
                    case '\\' -> sb.append("\\\\");
                    case '\n' -> sb.append("\\n");
                    case '\r' -> sb.append("\\r");
                    case '\t' -> sb.append("\\t");
                    default -> {
                        if (c < 0x20) {
                            sb.append(String.format("\\u%04x", (int) c));
                        } else {
                            sb.append(c);
                        }
                    }
                }
            }
            sb.append('"');
        } else if (v instanceof Number || v instanceof Boolean) {
            sb.append(v);
        } else if (v instanceof Map<?, ?> m) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                write(sb, String.valueOf(e.getKey()));
                sb.append(':');
                write(sb, e.getValue());
            }
            sb.append('}');
        } else if (v instanceof Iterable<?> it) {
            sb.append('[');
            boolean first = true;
            for (Object o : it) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                write(sb, o);
            }
            sb.append(']');
        } else {
            throw new IllegalArgumentException("cannot write " + v.getClass());
        }
    }
}
