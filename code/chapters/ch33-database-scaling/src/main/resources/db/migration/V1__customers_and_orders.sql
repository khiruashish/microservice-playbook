CREATE TABLE customers (
    id      bigint PRIMARY KEY,
    name    text   NOT NULL,
    address text   NOT NULL
);

CREATE TABLE orders (
    id            bigint      PRIMARY KEY,
    customer_id   bigint      NOT NULL,
    restaurant_id bigint      NOT NULL,
    amount_paise  int         NOT NULL,
    status        text        NOT NULL,
    created_at    timestamptz NOT NULL DEFAULT now()
);
