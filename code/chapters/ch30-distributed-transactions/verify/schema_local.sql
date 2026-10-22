-- The monolith baseline: everything in one database (cluster A).
DROP TABLE IF EXISTS orders, wallets, stock, debits;
CREATE TABLE orders (id bigint PRIMARY KEY, customer_id int NOT NULL, item_id int NOT NULL, amount int NOT NULL,
                     status text NOT NULL, created_at timestamptz NOT NULL DEFAULT clock_timestamp(), confirmed_at timestamptz);
CREATE TABLE wallets (customer_id int PRIMARY KEY, balance bigint NOT NULL CHECK (balance >= 0));
CREATE TABLE stock   (item_id int PRIMARY KEY, available int NOT NULL CHECK (available >= 0));
CREATE TABLE debits  (order_id bigint PRIMARY KEY, customer_id int NOT NULL, item_id int NOT NULL,
                      amount int NOT NULL, status text NOT NULL DEFAULT 'DEBITED');
INSERT INTO wallets SELECT c, 1000000000 FROM generate_series(1, 10000) c;
INSERT INTO stock   SELECT i, 1000000000 FROM generate_series(1, 10000) i;
