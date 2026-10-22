-- On its own: Flyway runs a migration containing CREATE INDEX CONCURRENTLY outside a transaction (mixing it with
-- transactional statements needs spring.flyway.mixed=true). Writes continue while it builds; measured: the
-- longest write during the build was 49-53 ms, against 0.8-1.2 s with a plain CREATE INDEX.
--
-- The build waits for every transaction that holds an older snapshot (a long report, a pg_dump), and under the
-- lock_timeout set in application.yml it gives up and leaves an INVALID index. Deliberately no IF NOT EXISTS: a
-- rerun would then "succeed" and keep the invalid index, which is maintained on every write but never used.
-- To recover: DROP INDEX CONCURRENTLY orders_restaurant_created; then flyway repair and migrate again.
CREATE INDEX CONCURRENTLY orders_restaurant_created ON orders (restaurant_id, created_at);
