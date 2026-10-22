-- payment/kitchen side (cluster B): wallets, dish stock for tonight's specials, and one debit row per order.
DROP TABLE IF EXISTS wallets, stock, debits;
CREATE TABLE wallets (customer_id int PRIMARY KEY, balance bigint NOT NULL CHECK (balance >= 0));
CREATE TABLE stock   (item_id int PRIMARY KEY, available int NOT NULL CHECK (available >= 0));
CREATE TABLE debits  (order_id bigint PRIMARY KEY, customer_id int NOT NULL, item_id int NOT NULL,
                      amount int NOT NULL, status text NOT NULL DEFAULT 'DEBITED');   -- DEBITED, REFUNDED
INSERT INTO wallets SELECT c, 1000000000 FROM generate_series(1, 10000) c;
INSERT INTO stock   SELECT i, 1000000000 FROM generate_series(1, 10000) i;
