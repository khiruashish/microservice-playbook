-- Chapter 13's event store, for PostgreSQL, plus the column that closes the position-gap problem.
-- (stream_id, stream_version) unique: optimistic concurrency. global_position: the order projections read in.
create table event_store (
    global_position bigint generated always as identity primary key,
    stream_id       varchar(100) not null,
    stream_version  bigint       not null,
    event_type      varchar(100) not null,
    schema_version  int          not null,
    payload         jsonb        not null,
    recorded_at     timestamptz  not null default now(),
    -- The transaction that wrote the row. A projection reads only rows whose transaction is older than every
    -- transaction still running, so a position taken by an uncommitted append can't be skipped.
    tx_id           xid8         not null default pg_current_xact_id(),
    constraint event_store_stream_version unique (stream_id, stream_version)
);

create table projection_checkpoint (
    projection      varchar(100) primary key,
    position        bigint       not null
);
