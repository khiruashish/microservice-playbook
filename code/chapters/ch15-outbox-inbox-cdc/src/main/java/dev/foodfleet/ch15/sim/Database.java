package dev.foodfleet.ch15.sim;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * A service's own database, reduced to what this chapter needs: tables of rows, atomic local
 * transactions (all changes or none), a sequence, and a write-ahead log (WAL) of committed changes
 * that a change-data-capture (CDC) connector can tail, as Debezium tails PostgreSQL's WAL.
 */
public final class Database {

    public enum Op { INSERT, UPDATE, DELETE }

    /** One committed change, in commit order. {@code lsn} is the log sequence number. */
    public record Change(long lsn, long txId, String table, Op op, String key, Object row) {
    }

    public static final class DuplicateKey extends RuntimeException {
        public DuplicateKey(String table, String key) {
            super("duplicate key " + key + " in " + table);
        }
    }

    private final String name;
    private final Map<String, Map<String, Object>> tables = new HashMap<>();
    private final List<Change> wal = new ArrayList<>();
    private long nextTxId = 1;
    private long nextSequence = 1;

    public Database(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    /**
     * Runs {@code work} in a local transaction. If it throws (a failure, or a simulated crash), no
     * change is applied: that is the only atomicity a service gets, and only inside its own database.
     */
    public <T> T inTransaction(Function<Tx, T> work) {
        Tx tx = new Tx(nextTxId++);
        T result = work.apply(tx);
        tx.commit();
        return result;
    }

    /** Like a database sequence: not transactional, so rolled-back transactions leave gaps. */
    public long nextSequence() {
        return nextSequence++;
    }

    public Map<String, Object> table(String table) {
        return Collections.unmodifiableMap(tables.getOrDefault(table, Map.of()));
    }

    public List<Change> walAfter(long lsn) {
        int from = (int) Math.min(wal.size(), Math.max(0, lsn));
        return List.copyOf(wal.subList(from, wal.size()));
    }

    public final class Tx {
        private final long id;
        private final List<Change> pending = new ArrayList<>();

        private Tx(long id) {
            this.id = id;
        }

        public Optional<Object> get(String table, String key) {
            for (int i = pending.size() - 1; i >= 0; i--) {
                Change c = pending.get(i);
                if (c.table().equals(table) && c.key().equals(key)) {
                    return c.op() == Op.DELETE ? Optional.empty() : Optional.of(c.row());
                }
            }
            return Optional.ofNullable(tables.getOrDefault(table, Map.of()).get(key));
        }

        public void insert(String table, String key, Object row) {
            if (get(table, key).isPresent()) {
                throw new DuplicateKey(table, key);
            }
            pending.add(new Change(0, id, table, Op.INSERT, key, row));
        }

        public void update(String table, String key, Object row) {
            if (get(table, key).isEmpty()) {
                throw new IllegalStateException("no row " + key + " in " + table);
            }
            pending.add(new Change(0, id, table, Op.UPDATE, key, row));
        }

        public void delete(String table, String key) {
            pending.add(new Change(0, id, table, Op.DELETE, key, null));
        }

        private void commit() {
            for (Change c : pending) {
                Map<String, Object> rows = tables.computeIfAbsent(c.table(), t -> new LinkedHashMap<>());
                if (c.op() == Op.DELETE) {
                    rows.remove(c.key());
                } else {
                    rows.put(c.key(), c.row());
                }
                wal.add(new Change(wal.size() + 1L, id, c.table(), c.op(), c.key(), c.row()));
            }
        }
    }
}
