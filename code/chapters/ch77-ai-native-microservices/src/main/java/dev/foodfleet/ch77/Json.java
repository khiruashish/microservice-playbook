package dev.foodfleet.ch77;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Just enough JSON for the lab's flat messages: read a top-level string or number field, quote a string. */
public final class Json {

    private Json() {
    }

    public static String string(String json, String field) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json);
        return m.find() ? m.group(1).replace("\\\"", "\"").replace("\\\\", "\\") : null;
    }

    public static long number(String json, String field, long otherwise) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*(-?[0-9]+)").matcher(json);
        return m.find() ? Long.parseLong(m.group(1)) : otherwise;
    }

    /** The raw JSON value of a field (object, array, string or number), found by bracket matching. */
    public static String raw(String json, String field) {
        int i = json.indexOf("\"" + field + "\"");
        if (i < 0) {
            return null;
        }
        i = json.indexOf(':', i) + 1;
        while (Character.isWhitespace(json.charAt(i))) {
            i++;
        }
        char c = json.charAt(i);
        if (c != '{' && c != '[') {
            int end = i;
            if (c == '"') {
                end = i + 1;
                while (json.charAt(end) != '"' || json.charAt(end - 1) == '\\') {
                    end++;
                }
                return json.substring(i, end + 1);
            }
            while (end < json.length() && ",}] \n".indexOf(json.charAt(end)) < 0) {
                end++;
            }
            return json.substring(i, end);
        }
        int depth = 0;
        boolean inString = false;
        for (int j = i; j < json.length(); j++) {
            char x = json.charAt(j);
            if (x == '"' && json.charAt(j - 1) != '\\') {
                inString = !inString;
            } else if (!inString && (x == '{' || x == '[')) {
                depth++;
            } else if (!inString && (x == '}' || x == ']') && --depth == 0) {
                return json.substring(i, j + 1);
            }
        }
        return null;
    }

    public static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }
}
