create table orders (
    id                     varchar(20)  primary key,
    customer_id            varchar(20)  not null,
    restaurant             varchar(60)  not null,
    total_paise            bigint       not null,
    status                 varchar(20)  not null,
    placed_at              timestamptz  not null,
    delivery_instructions  varchar(500),
    version                bigint       not null default 1     -- the ETag
);
-- keyset pagination: newest first per customer, id breaks ties between orders placed in the same instant
create index orders_customer_placed on orders (customer_id, placed_at desc, id desc);
