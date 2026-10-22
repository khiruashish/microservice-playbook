-- payment-service owns this schema; no other service reads it (chapter 12).
create table if not exists payment (
    id                varchar(40)  primary key,
    order_id          varchar(64)  not null,
    amount_paise      bigint       not null,
    method_type       varchar(16)  not null,
    method_reference  varchar(300) not null,
    idempotency_key   varchar(128) not null unique,
    created_at        timestamp    not null,
    status            varchar(24)  not null,
    gateway_reference varchar(128),
    decline_reason    varchar(256),
    version           bigint       not null
);
create index if not exists payment_order_idx on payment (order_id);

create table if not exists payment_refund (
    payment_id   varchar(40)  not null references payment (id),
    seq          int          not null,
    amount_paise bigint       not null,
    reason       varchar(256) not null,
    refunded_at  timestamp    not null,
    primary key (payment_id, seq)
);
