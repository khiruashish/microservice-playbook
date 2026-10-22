-- order-service: the business table and the outbox in the same database, so they commit together (ADR-015)
CREATE TABLE orders (
    id           text PRIMARY KEY,
    customer_id  text NOT NULL,
    total_paise  bigint NOT NULL CHECK (total_paise > 0),
    status       text NOT NULL,
    placed_at    timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE outbox (
    position        bigserial PRIMARY KEY,               -- publishing order
    id              uuid NOT NULL UNIQUE,                -- the event id consumers deduplicate on
    aggregate_type  text NOT NULL,                       -- Debezium routes on it: order -> foodfleet.ordering.order-events
    aggregate_id    text NOT NULL,                       -- the Kafka key: one order's events stay in order
    type            text NOT NULL,
    payload         jsonb NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    published_at    timestamptz                          -- null until the polling relay has published it
);
-- the relay only ever reads unpublished rows: keep that index small
CREATE INDEX outbox_unpublished ON outbox (position) WHERE published_at IS NULL;
