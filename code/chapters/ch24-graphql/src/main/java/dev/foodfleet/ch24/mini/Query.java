package dev.foodfleet.ch24.mini;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A parsed GraphQL selection: the subset this chapter needs (fields, literal arguments, nested
 * selections). Real servers (graphql-java under Spring for GraphQL) also handle variables,
 * fragments, aliases and directives; the execution ideas are the same.
 */
public record Query(List<Field> fields) {

    public record Field(String name, Map<String, Object> arguments, List<Field> selection) {
        public boolean isLeaf() {
            return selection.isEmpty();
        }
    }

    /** Parses {@code query Name { a b(first: 3) { c } }} or just {@code { ... }}. */
    public static Query parse(String text) {
        Parser p = new Parser(text);
        p.skip();
        if (p.peekWord("query")) {
            p.word();
            p.skip();
            if (Character.isLetter(p.peek())) {
                p.word();                                   // operation name
            }
        }
        return new Query(p.selectionSet());
    }

    /** Deepest nesting of selections (a top-level field is depth 1). */
    public int depth() {
        return depth(fields);
    }

    private static int depth(List<Field> fields) {
        int max = 0;
        for (Field f : fields) {
            max = Math.max(max, 1 + depth(f.selection()));
        }
        return max;
    }

    private static final class Parser {
        private final String s;
        private int i;

        Parser(String s) {
            this.s = s;
        }

        List<Field> selectionSet() {
            expect('{');
            List<Field> fields = new ArrayList<>();
            skip();
            while (peek() != '}') {
                fields.add(field());
                skip();
            }
            expect('}');
            return fields;
        }

        Field field() {
            String name = word();
            skip();
            Map<String, Object> args = new LinkedHashMap<>();
            if (peek() == '(') {
                i++;
                skip();
                while (peek() != ')') {
                    String arg = word();
                    skip();
                    expect(':');
                    skip();
                    args.put(arg, value());
                    skip();
                }
                i++;
                skip();
            }
            List<Field> selection = peek() == '{' ? selectionSet() : List.of();
            return new Field(name, args, selection);
        }

        Object value() {
            if (peek() == '"') {
                int end = s.indexOf('"', i + 1);
                String v = s.substring(i + 1, end);
                i = end + 1;
                return v;
            }
            int start = i;
            while (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '-')) {
                i++;
            }
            if (start == i) {
                throw new IllegalArgumentException("unsupported value at " + i + ": " + s.substring(i));
            }
            return Integer.parseInt(s.substring(start, i));
        }

        String word() {
            int start = i;
            while (i < s.length() && (Character.isLetterOrDigit(s.charAt(i)) || s.charAt(i) == '_')) {
                i++;
            }
            if (start == i) {
                throw new IllegalArgumentException("expected a name at " + i + ": " + s.substring(i));
            }
            return s.substring(start, i);
        }

        boolean peekWord(String w) {
            return s.startsWith(w, i) && !Character.isLetterOrDigit(s.charAt(i + w.length()));
        }

        char peek() {
            if (i >= s.length()) {
                throw new IllegalArgumentException("unexpected end of document");
            }
            return s.charAt(i);
        }

        void expect(char c) {
            skip();
            if (peek() != c) {
                throw new IllegalArgumentException("expected '" + c + "' at " + i + ": " + s.substring(i));
            }
            i++;
        }

        void skip() {                                       // whitespace, commas and comments are insignificant
            while (i < s.length()) {
                char c = s.charAt(i);
                if (Character.isWhitespace(c) || c == ',') {
                    i++;
                } else if (c == '#') {
                    while (i < s.length() && s.charAt(i) != '\n') {
                        i++;
                    }
                } else {
                    return;
                }
            }
        }
    }
}
