-- Pagination benchmark data: 2,000,000 orders from 50,000 customers over two years.
-- Same table and index as the service's Flyway migration (src/main/resources/db/migration).
drop table if exists orders;
create table orders (
    id           varchar(20) primary key,
    customer_id  varchar(20) not null,
    restaurant   varchar(60) not null,
    total_paise  bigint      not null,
    status       varchar(20) not null,
    placed_at    timestamptz not null,
    version      bigint      not null default 1
);
insert into orders (id, customer_id, restaurant, total_paise, status, placed_at)
select 'o-' || g,
       'c-' || (g % 50000),
       'Restaurant ' || (g % 4000),
       19900 + (g::bigint * 7919 % 150000),
       case when g % 50 = 0 then 'CANCELLED' else 'DELIVERED' end,
       timestamptz '2024-10-01 00:00:00+00' + (g * interval '31.5 seconds')
from generate_series(1, 2000000) as g;
-- one index serves both listings: newest first, ties broken by id
create index orders_placed on orders (placed_at desc, id desc);
create index orders_customer_placed on orders (customer_id, placed_at desc, id desc);
vacuum analyze orders;
