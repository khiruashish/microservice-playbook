-- ordering-service: the business table and the outbox, in the same database
create table orders (
    id           varchar(40) primary key,
    customer_id  varchar(40) not null,
    total_paise  bigint      not null check (total_paise > 0),
    status       varchar(20) not null,
    placed_at    timestamptz not null default now()
);

create table outbox (
    position        bigserial   primary key,            -- publishing order
    id              uuid        not null unique,        -- message id: consumers deduplicate on it
    aggregate_type  varchar(40) not null,               -- becomes the topic (order -> foodfleet.order.events)
    aggregate_id    varchar(40) not null,               -- becomes the message key (partition = ordering)
    type            varchar(60) not null,
    payload         jsonb       not null,
    created_at      timestamptz not null default now(),
    published_at    timestamptz                         -- null = not yet published (polling relay only)
);
-- the relay's query only ever touches unpublished rows: keep that index small
create index outbox_unpublished on outbox (position) where published_at is null;

-- loyalty-service (in its own database in production; here in the same one to keep the demo small)
create table inbox (
    consumer      varchar(60) not null,
    message_id    uuid        not null,
    processed_at  timestamptz not null default now(),
    primary key (consumer, message_id)                  -- the deduplication
);

create table loyalty_points (
    customer_id  varchar(40) primary key,
    points       bigint      not null
);
