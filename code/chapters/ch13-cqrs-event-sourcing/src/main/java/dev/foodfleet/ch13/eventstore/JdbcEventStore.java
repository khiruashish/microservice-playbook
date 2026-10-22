package dev.foodfleet.ch13.eventstore;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The event store on a relational database: one append-only table. The unique constraint on
 * (stream_id, stream_version) does the concurrency control; no locks are held between commands.
 *
 * <p>Caveat: an identity column gives positions in commit order only with one writer at a time.
 * With concurrent writers, a projection can see position 12 before 11 commits and skip 11.
 * Production stores serialise appends, use a commit-ordered sequence, or have projections wait
 * for gaps to close. (EventStoreDB and Axon Server handle this for you.)
 */
public final class JdbcEventStore implements EventStore {

    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final Clock clock;

    public JdbcEventStore(JdbcClient jdbc, TransactionTemplate tx, Clock clock) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.clock = clock;
    }

    @Override
    public long append(String streamId, long expectedVersion, List<NewEvent> events) {
        return tx.execute(status -> {
            long current = jdbc.sql("select coalesce(max(stream_version), 0) from event_store where stream_id = :s")
                    .param("s", streamId).query(Long.class).single();
            if (current != expectedVersion) {
                throw new ConcurrencyException(streamId, expectedVersion, current);
            }
            long version = expectedVersion;
            try {
                for (NewEvent e : events) {
                    version++;
                    jdbc.sql("""
                            insert into event_store (stream_id, stream_version, event_type, schema_version, payload, recorded_at)
                            values (:s, :v, :t, :sv, :p, :at)""")
                            .param("s", streamId).param("v", version).param("t", e.type())
                            .param("sv", e.schemaVersion()).param("p", encode(e.payload()))
                            .param("at", Timestamp.from(clock.instant()))
                            .update();
                }
            } catch (DuplicateKeyException raced) {
                // another writer inserted the same version between our check and our insert
                throw new ConcurrencyException(streamId, expectedVersion, expectedVersion + 1);
            }
            return version;
        });
    }

    @Override
    public List<StoredEvent> readStream(String streamId, long afterVersion) {
        return jdbc.sql("select * from event_store where stream_id = :s and stream_version > :v order by stream_version")
                .param("s", streamId).param("v", afterVersion)
                .query((rs, n) -> row(rs)).list();
    }

    @Override
    public List<StoredEvent> readAll(long afterPosition, int max) {
        return jdbc.sql("select * from event_store where global_position > :p order by global_position limit :max")
                .param("p", afterPosition).param("max", max)
                .query((rs, n) -> row(rs)).list();
    }

    private static StoredEvent row(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new StoredEvent(rs.getString("stream_id"), rs.getLong("stream_version"), rs.getLong("global_position"),
                rs.getString("event_type"), rs.getInt("schema_version"), decode(rs.getString("payload")),
                rs.getTimestamp("recorded_at").toInstant());
    }

    /** {@code k=v&k=v}, URL-encoded: dependency-free. Real stores use JSON (jsonb in PostgreSQL). */
    static String encode(Map<String, String> payload) {
        return new TreeMap<>(payload).entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    static Map<String, String> decode(String text) {
        Map<String, String> payload = new LinkedHashMap<>();
        if (text.isEmpty()) {
            return payload;
        }
        for (String pair : text.split("&")) {
            int eq = pair.indexOf('=');
            payload.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
        }
        return payload;
    }
}
