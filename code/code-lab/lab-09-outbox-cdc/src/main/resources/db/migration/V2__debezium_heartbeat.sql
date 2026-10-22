-- only needed with Debezium: a row it updates every 10 s, so the replication slot advances even when no orders are
-- placed, and PostgreSQL can release WAL (chapter 15's monitored, capped slots; ADR-034)
CREATE TABLE debezium_heartbeat (
    id  int PRIMARY KEY,
    ts  timestamptz NOT NULL
);
