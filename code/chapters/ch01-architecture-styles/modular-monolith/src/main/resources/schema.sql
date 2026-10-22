-- One database, one schema per module. Each schema is written only by its owning module,
-- which is what makes a later extraction a schema move rather than a data untangling exercise.
-- (Spring Modulith creates its own EVENT_PUBLICATION table; see application.yml.)

create schema if not exists ordering;
create schema if not exists kitchen;

create table if not exists ordering.orders (
    id            uuid primary key,
    customer_id   varchar(64)  not null,
    restaurant_id varchar(64)  not null,
    status        varchar(20)  not null,
    total_paise   bigint       not null,
    placed_at     timestamp    not null
);

create table if not exists ordering.order_line (
    order_id          uuid         not null references ordering.orders (id),
    line_no           int          not null,
    item_id           varchar(64)  not null,
    item_name         varchar(200) not null,
    quantity          int          not null,
    unit_price_paise  bigint       not null,
    primary key (order_id, line_no)
);

-- No foreign key to ordering.orders: modules never reference each other's tables.
create table if not exists kitchen.ticket (
    id            uuid primary key,
    order_id      uuid         not null unique,
    restaurant_id varchar(64)  not null,
    status        varchar(20)  not null,
    created_at    timestamp    not null
);

create index if not exists ix_ticket_restaurant_status on kitchen.ticket (restaurant_id, status);
