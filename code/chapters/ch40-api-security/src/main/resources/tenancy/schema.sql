-- FoodFleet orders, shared by every tenant, isolated by PostgreSQL row-level security.
-- Run as the schema owner (a migration role). The application connects as orders_app, which owns nothing.

CREATE TABLE orders (
    tenant_id   text        NOT NULL,
    id          bigint      NOT NULL,
    customer_id text        NOT NULL,
    total_paise bigint      NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, id)               -- tenant first: every index serves the tenant predicate
);

ALTER TABLE orders ENABLE ROW LEVEL SECURITY;
ALTER TABLE orders FORCE ROW LEVEL SECURITY;  -- the owner too: migrations and owner-run jobs see one tenant

-- current_setting(..., true) returns NULL when the setting was never set in this session, and '' after a
-- transaction that set it locally has ended; nullif maps both to NULL, and "tenant_id = NULL" matches nothing.
-- Without nullif the second case is an error (''::text compares fine here, but a uuid or bigint key would fail
-- the cast), and without missing_ok=true the first case is.
CREATE POLICY tenant_isolation ON orders
    USING (tenant_id = nullif(current_setting('app.tenant_id', true), ''))
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), ''));

CREATE SEQUENCE order_ids;

CREATE ROLE orders_app LOGIN NOBYPASSRLS;     -- password set by the secrets platform (chapter 39)
GRANT SELECT, INSERT, UPDATE, DELETE ON orders TO orders_app;
GRANT USAGE ON SEQUENCE order_ids TO orders_app;
