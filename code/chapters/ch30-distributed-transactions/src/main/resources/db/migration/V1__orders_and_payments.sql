-- Two schemas stand in for two services' databases. No transaction in the code spans both.

-- order-service
CREATE SCHEMA orders;

CREATE TABLE orders.orders (
    id           bigserial   PRIMARY KEY,
    customer_id  bigint      NOT NULL,
    item_id      bigint      NOT NULL,
    amount_paise int         NOT NULL CHECK (amount_paise > 0),
    status       text        NOT NULL CHECK (status IN ('PENDING', 'CONFIRMED', 'REJECTED')),
    reason       text,
    created_at   timestamptz NOT NULL DEFAULT clock_timestamp(),
    decided_at   timestamptz
);
CREATE INDEX orders_pending ON orders.orders (created_at) WHERE status = 'PENDING';

-- written in the same transaction as the order; claimed and deleted by OrderRelay
CREATE TABLE orders.outbox (
    id           bigserial PRIMARY KEY,
    order_id     bigint NOT NULL,
    customer_id  bigint NOT NULL,
    item_id      bigint NOT NULL,
    amount_paise int    NOT NULL
);

-- A two-phase-commit coordinator's decision log (only for systems that use 2PC; see indoubt/).
CREATE TABLE orders.tm_log (
    gid       text        PRIMARY KEY,
    decision  text        NOT NULL CHECK (decision IN ('COMMIT', 'ROLLBACK')),
    logged_at timestamptz NOT NULL DEFAULT clock_timestamp()
);

-- payment-service
CREATE SCHEMA payments;

CREATE TABLE payments.wallets (customer_id bigint PRIMARY KEY, balance_paise bigint NOT NULL CHECK (balance_paise >= 0));
CREATE TABLE payments.stock   (item_id bigint PRIMARY KEY, available int NOT NULL CHECK (available >= 0));

-- one row per order, whatever the outcome: the idempotency record and the decision
CREATE TABLE payments.debits (
    order_id     bigint      PRIMARY KEY,
    customer_id  bigint      NOT NULL,
    item_id      bigint      NOT NULL,
    amount_paise int         NOT NULL,
    status       text        NOT NULL CHECK (status IN ('DEBITED', 'REJECTED')),
    reason       text,
    decided_at   timestamptz NOT NULL DEFAULT clock_timestamp()
);
