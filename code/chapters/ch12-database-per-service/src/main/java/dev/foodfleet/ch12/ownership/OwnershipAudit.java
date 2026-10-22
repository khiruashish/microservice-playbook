package dev.foodfleet.ch12.ownership;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Database per service, checked against what services actually run. Input: which service owns
 * each table, and the SQL each service executed (for example from pg_stat_statements, one
 * {@code service|statement} line each). Output: every statement that touches another service's
 * table, which is exactly the coupling that blocks splitting the database.
 */
public final class OwnershipAudit {

    public enum Access { READ, WRITE }

    public record Violation(String service, String table, String owner, Access access, String statement) {
    }

    public record Statement(String service, String sql) {
    }

    public record Report(List<Violation> violations, Set<String> unknownTables, int statementsChecked) {

        /** Pairs (reader → owner) with counts, the input for a migration plan. */
        public Map<String, Integer> couplingByPair() {
            Map<String, Integer> pairs = new TreeMap<>();
            violations.forEach(v -> pairs.merge(v.service() + " -> " + v.owner() + " (" + v.access().name().toLowerCase(Locale.ROOT) + ")", 1, Integer::sum));
            return pairs;
        }
    }

    private final Map<String, String> ownerOfTable;

    /** @param ownerOfTable table (optionally schema-qualified, lower case) → owning service */
    public OwnershipAudit(Map<String, String> ownerOfTable) {
        this.ownerOfTable = Map.copyOf(ownerOfTable);
    }

    public static List<Statement> parseLog(List<String> lines) {
        List<Statement> statements = new ArrayList<>();
        for (String raw : lines) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int bar = line.indexOf('|');
            if (bar <= 0) {
                throw new IllegalArgumentException("Expected service|sql: " + raw);
            }
            statements.add(new Statement(line.substring(0, bar).strip(), line.substring(bar + 1).strip()));
        }
        return statements;
    }

    public Report audit(List<Statement> statements) {
        List<Violation> violations = new ArrayList<>();
        Set<String> unknown = new TreeSet<>();
        for (Statement s : statements) {
            TableReferences.Refs refs = TableReferences.of(s.sql());
            check(s, refs.writes(), Access.WRITE, violations, unknown);
            Set<String> readsOnly = new TreeSet<>(refs.reads());
            readsOnly.removeAll(refs.writes());
            check(s, readsOnly, Access.READ, violations, unknown);
        }
        return new Report(violations, unknown, statements.size());
    }

    private void check(Statement s, Set<String> tables, Access access, List<Violation> out, Set<String> unknown) {
        for (String table : tables) {
            String owner = ownerOf(table);
            if (owner == null) {
                unknown.add(table);
            } else if (!owner.equals(s.service())) {
                out.add(new Violation(s.service(), table, owner, access, s.sql()));
            }
        }
    }

    /** Exact match first, then the unqualified table name. */
    String ownerOf(String table) {
        String owner = ownerOfTable.get(table);
        if (owner == null && table.contains(".")) {
            owner = ownerOfTable.get(table.substring(table.indexOf('.') + 1));
        }
        return owner;
    }
}
