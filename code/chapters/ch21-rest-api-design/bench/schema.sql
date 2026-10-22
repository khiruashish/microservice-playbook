-- Benchmark data: 2,000,000 orders across 500 restaurants; restaurant 42 is a busy one with
-- ~200,000 orders (a cloud kitchen), restaurant 77 has ~4,000.
drop table if exists restaurant_orders;
create table restaurant_orders (
    id            bigserial   primary key,
    restaurant_id int         not null,
    customer_id   bigint      not null,
    status        text        not null,
    total_paise   bigint      not null,
    placed_at     timestamptz not null
);

insert into restaurant_orders (restaurant_id, customer_id, status, total_paise, placed_at)
select case when g % 10 = 0 then 42 else (g % 500) + 1 end,
       (g::bigint * 7919) % 250000,
       (array['DELIVERED','DELIVERED','DELIVERED','CANCELLED','REFUNDED'])[1 + g % 5],
       9900 + (g::bigint * 37) % 90000,
       timestamptz '2026-10-01 00:00:00+05:30' - make_interval(secs => g * 3)
from generate_series(1, 2000000) g;

-- The index both pagination styles use: equality on restaurant, then the sort order.
create index restaurant_orders_by_restaurant_time on restaurant_orders (restaurant_id, placed_at desc, id desc);
vacuum analyze restaurant_orders;
