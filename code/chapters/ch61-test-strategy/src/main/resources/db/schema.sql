create table orders (
    id              bigserial primary key,
    idempotency_key varchar(64)    not null unique,
    customer_id     varchar(64)    not null,
    restaurant_id   integer        not null,
    subtotal        numeric(10, 2) not null,
    discount        numeric(10, 2) not null,
    tax             numeric(10, 2) not null,
    total           numeric(10, 2) not null,
    status          varchar(16)    not null,
    created_at      timestamp with time zone not null default current_timestamp
);

create table order_lines (
    order_id   bigint         not null references orders (id),
    item_id    varchar(64)    not null,
    quantity   integer        not null check (quantity between 1 and 20),
    unit_price numeric(10, 2) not null,
    primary key (order_id, item_id)
);
