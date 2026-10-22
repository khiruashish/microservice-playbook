-- VALIDATE scans the table under a lock that lets reads and writes continue. It must not share a transaction
-- with V4: the ADD CONSTRAINT's exclusive lock would be held for the whole scan.
ALTER TABLE orders VALIDATE CONSTRAINT orders_amount_positive;
