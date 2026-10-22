-- The reporting service's own database: built from other services' events, never written by them.

-- One row per order, the latest version seen. Only what reports need, denormalised (city, business day).
CREATE TABLE order_facts (
    order_id      bigint      PRIMARY KEY,
    version       int         NOT NULL,
    restaurant_id bigint      NOT NULL,
    city          text        NOT NULL,
    status        text        NOT NULL,
    amount_paise  bigint      NOT NULL,
    placed_at     timestamptz NOT NULL,
    business_day  date        NOT NULL,      -- placed_at in the city's time zone, not UTC
    updated_at    timestamptz NOT NULL DEFAULT now()
);

-- Kept current by deltas as facts change: a delivered order adds itself, a refund takes itself back out.
CREATE TABLE daily_restaurant_revenue (
    business_day     date   NOT NULL,
    restaurant_id    bigint NOT NULL,
    city             text   NOT NULL,
    delivered_orders int    NOT NULL DEFAULT 0,
    revenue_paise    bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (business_day, restaurant_id)
);
CREATE INDEX daily_restaurant_revenue_city_day ON daily_restaurant_revenue (city, business_day);

-- How far the projection has got: the event time of the newest event applied. Reports show it.
CREATE TABLE projection_status (
    projection    text        PRIMARY KEY,
    last_event_at timestamptz,
    applied       bigint      NOT NULL DEFAULT 0
);
INSERT INTO projection_status (projection) VALUES ('order-facts');

-- Monthly totals for the finance dashboard; refreshed CONCURRENTLY, which needs a unique index.
CREATE MATERIALIZED VIEW city_monthly_revenue AS
SELECT city, date_trunc('month', business_day)::date AS month,
       sum(delivered_orders)::bigint AS delivered_orders, sum(revenue_paise)::bigint AS revenue_paise
  FROM daily_restaurant_revenue
 GROUP BY 1, 2;
CREATE UNIQUE INDEX city_monthly_revenue_key ON city_monthly_revenue (city, month);
