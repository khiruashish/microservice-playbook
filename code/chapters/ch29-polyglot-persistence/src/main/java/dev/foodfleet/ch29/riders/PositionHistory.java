package dev.foodfleet.ch29.riders;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Position history in PostgreSQL, written in batches: one statement per batch, the rows passed as one
 * JSON array and unpacked by {@code jsonb_to_recordset}.
 */
@Repository
public class PositionHistory {

    static final String INSERT = """
            INSERT INTO rider_pings (rider_id, recorded_at, lat, lon, speed_kmh)
            SELECT rider_id, recorded_at, lat, lon, speed_kmh
              FROM jsonb_to_recordset(CAST(:pings AS jsonb))
                   AS p(rider_id bigint, recorded_at timestamptz, lat double precision, lon double precision, speed_kmh real)""";

    private final JdbcClient jdbc;

    public PositionHistory(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public int record(List<Ping> pings) {
        if (pings.isEmpty()) {
            return 0;
        }
        return jdbc.sql(INSERT).param("pings", toJson(pings)).update();
    }

    /** Numbers and ISO-8601 instants only, so formatting by hand is safe and avoids a mapper per batch. */
    static String toJson(List<Ping> pings) {
        StringBuilder json = new StringBuilder(pings.size() * 110).append('[');
        for (int i = 0; i < pings.size(); i++) {
            Ping p = pings.get(i);
            if (i > 0) {
                json.append(',');
            }
            json.append(String.format(Locale.ROOT,
                    "{\"rider_id\":%d,\"recorded_at\":\"%s\",\"lat\":%.6f,\"lon\":%.6f,\"speed_kmh\":%.1f}",
                    p.riderId(), p.recordedAt(), p.lat(), p.lon(), p.speedKmh()));
        }
        return json.append(']').toString();
    }

    /** One rider's track, oldest first. */
    public List<Ping> track(long riderId, Instant from, Instant to) {
        return jdbc.sql("""
                SELECT rider_id, lat, lon, speed_kmh, recorded_at FROM rider_pings
                 WHERE rider_id = :rider AND recorded_at >= :from AND recorded_at < :to
                 ORDER BY recorded_at""")
                .param("rider", riderId).param("from", from.atOffset(ZoneOffset.UTC)).param("to", to.atOffset(ZoneOffset.UTC))
                .query((rs, n) -> new Ping(rs.getLong(1), rs.getDouble(2), rs.getDouble(3), rs.getFloat(4),
                        rs.getObject(5, OffsetDateTime.class).toInstant()))
                .list();
    }
}
