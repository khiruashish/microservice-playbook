-- Used by the tests (Testcontainers init script) and verify/rls.sh; in production the platform team runs it.
-- Roles are cluster-wide, so the platform creates them, not the service's migrations. The application's role owns
-- nothing and can't bypass row-level security; the migration role owns the tables.
CREATE ROLE orders_owner NOLOGIN;
CREATE ROLE orders_owner_login LOGIN IN ROLE orders_owner;   -- Flyway logs in as this and SETs ROLE orders_owner
CREATE ROLE orders_app LOGIN NOBYPASSRLS;                    -- passwords from the secrets platform (lab 05, ADR-039)
GRANT CREATE ON SCHEMA public TO orders_owner;               -- PostgreSQL 15+: public isn't writable by default
