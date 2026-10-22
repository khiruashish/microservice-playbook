\set id random(1, 2000000)
UPDATE orders SET status = 'DELIVERED' WHERE id = :id;
INSERT INTO orders (customer_id, restaurant_id, amount_paise, status, created_at) VALUES (:id % 50000, :id % 2000, 25000, 'NEW', now());
