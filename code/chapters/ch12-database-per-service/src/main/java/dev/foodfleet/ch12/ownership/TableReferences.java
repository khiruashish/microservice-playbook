package dev.foodfleet.ch12.ownership;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds which tables a SQL statement reads and writes. Deliberately simple (regex over FROM, JOIN,
 * INSERT INTO, UPDATE, DELETE FROM, MERGE INTO): good enough for statements exported from
 * pg_stat_statements or a slow-query log, not a full SQL parser. Schema prefixes are kept
 * ({@code ordering.orders}); quoting and case are normalised.
 */
public final class TableReferences {

    public record Refs(Set<String> reads, Set<String> writes) {
    }

    private static final String NAME = "((?:\"?[A-Za-z_][\\w$]*\"?\\.)?\"?[A-Za-z_][\\w$]*\"?)";
    private static final Pattern WRITE = Pattern.compile(
            "\\b(?:insert\\s+into|update|delete\\s+from|merge\\s+into)\\s+" + NAME, Pattern.CASE_INSENSITIVE);
    private static final Pattern READ = Pattern.compile("\\b(?:from|join)\\s+" + NAME, Pattern.CASE_INSENSITIVE);

    private TableReferences() {
    }

    public static Refs of(String sql) {
        String cleaned = sql.replaceAll("--[^\\n]*", " ").replaceAll("(?s)/\\*.*?\\*/", " ");
        Set<String> writes = collect(WRITE, cleaned);
        // "DELETE FROM t" is a write, not a read of t: hide that FROM from the read pattern
        Set<String> reads = collect(READ, cleaned.replaceAll("(?i)\\bdelete\\s+from\\b", "delete_target"));
        return new Refs(reads, writes);
    }

    private static Set<String> collect(Pattern pattern, String sql) {
        Set<String> tables = new LinkedHashSet<>();
        Matcher m = pattern.matcher(sql);
        while (m.find()) {
            String name = m.group(1).replace("\"", "").toLowerCase(Locale.ROOT);
            if (!name.equals("select") && !name.equals("lateral")) {
                tables.add(name);
            }
        }
        return tables;
    }
}
