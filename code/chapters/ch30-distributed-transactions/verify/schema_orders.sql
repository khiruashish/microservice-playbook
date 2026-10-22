-- order-service's database (cluster A): orders, the outbox, and the 2PC coordinator's decision log.
DROP TABLE IF EXISTS orders, order_outbox, tm_log;
CREATE TABLE orders (
    id           bigint      PRIMARY KEY,
    customer_id  int         NOT NULL,
    item_id      int         NOT NULL,
    amount       int         NOT NULL,
    status       text        NOT NULL,          -- PENDING, CONFIRMED, REJECTED
    created_at   timestamptz NOT NULL DEFAULT clock_timestamp(),
    confirmed_at timestamptz
);
CREATE TABLE order_outbox (
    id          bigserial PRIMARY KEY,
    order_id    bigint NOT NULL,
    customer_id int    NOT NULL,
    item_id     int    NOT NULL,
    amount      int    NOT NULL
);
CREATE TABLE tm_log (gid text PRIMARY KEY, decision text NOT NULL, logged_at timestamptz NOT NULL DEFAULT clock_timestamp());
