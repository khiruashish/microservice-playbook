-- order-service's orders, one table for every tenant (restaurant groups on FoodFleet's partner plan), isolated by
-- PostgreSQL row-level security (ADR-040, ADR-075). verify/rls.sh runs this file on PostgreSQL 16.
CREATE TABLE orders (
    tenant_id   text        NOT NULL,
    id          bigint      NOT NULL,
    customer_id text        NOT NULL,
    total_paise bigint      NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, id)                  -- tenant first: every index serves the tenant predicate
);
CREATE INDEX orders_recent ON orders (tenant_id, created_at DESC);

ALTER TABLE orders ENABLE ROW LEVEL SECURITY;
ALTER TABLE orders FORCE ROW LEVEL SECURITY;     -- the table's owner too, not only orders_app

-- NULL when the setting was never set on this connection, '' after a transaction-local value ended: both mean
-- "no tenant", and tenant_id = NULL matches no row. A request that forgot its tenant sees nothing.
CREATE POLICY tenant_isolation ON orders
    USING (tenant_id = nullif(current_setting('app.tenant_id', true), ''))
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), ''));

CREATE SEQUENCE order_ids;
GRANT SELECT, INSERT, UPDATE, DELETE ON orders TO orders_app;
GRANT USAGE ON SEQUENCE order_ids TO orders_app;
