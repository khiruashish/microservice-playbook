-- The order service's write path: place an order, then move it on (one transaction each, like the API).
\set r random(1, 20000)
\set amt random(15000, 100000)
INSERT INTO orders (restaurant_id, customer_id, status, amount_paise, placed_at, updated_at)
VALUES (:r, 1, 'PLACED', :amt, now(), now()) RETURNING id AS oid \gset
UPDATE orders SET status = 'DELIVERED', updated_at = now() WHERE id = :oid;
