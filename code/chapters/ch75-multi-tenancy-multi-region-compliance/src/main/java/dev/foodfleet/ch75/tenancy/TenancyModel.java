package dev.foodfleet.ch75.tenancy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

/**
 * Three ways to keep restaurant chains' (tenants') orders apart in PostgreSQL. Each provisions a tenant, loads its
 * orders, migrates every tenant's schema, and answers two tenant-scoped queries: the last 20 orders, and revenue over 30 days.
 */
public sealed interface TenancyModel permits TenancyModel.SharedTable, TenancyModel.SchemaPerTenant, TenancyModel.DatabasePerTenant {

    String TABLE = "CREATE TABLE %s (id bigserial PRIMARY KEY,%s placed_at timestamptz NOT NULL, total_paise bigint NOT NULL, customer_email text NOT NULL)";
    String LOAD = "INSERT INTO %s (%s placed_at, total_paise, customer_email) SELECT %s timestamptz '2026-03-01' - g * interval '17 minutes',"
            + " 15000 + (g::bigint * 7919) %% 90000, 'customer' || (g %% 500) || '@t%d.example' FROM generate_series(1, %d) g";

    /**
     * The tenant the current transaction belongs to, or NULL. {@code current_setting(..., true)} is NULL before the
     * setting was ever used on a connection, but an empty string after a transaction-local value went away, hence NULLIF.
     */
    String POLICY = "NULLIF(current_setting('app.tenant_id', true), '')::int";

    String name();

    void provision(int tenant) throws SQLException;

    void load(int tenant, int rows) throws SQLException;

    /** Adds a currency column with a default, and an index on it, for every tenant. */
    void migrate(int tenants) throws SQLException;

    long lastOrders(int tenant) throws SQLException;

    long revenue(int tenant) throws SQLException;

    /** Connections this model holds open to serve every tenant from one application instance. */
    int connections();

    static long first(PreparedStatement s) throws SQLException {
        try (ResultSet r = s.executeQuery()) {
            long n = 0;
            while (r.next()) {
                n += r.getLong(1);
            }
            return n;
        }
    }

    /** One table with a tenant_id column, protected by a forced row-level security policy; queried as a non-owner role. */
    final class SharedTable implements TenancyModel {
        private final Cluster cluster;
        private final Connection admin;
        private final Connection app;

        public SharedTable(Cluster cluster) throws SQLException {
            this.cluster = cluster;
            try (Connection c = cluster.connect("postgres"); Statement s = c.createStatement()) {
                s.execute("CREATE DATABASE shared");
            }
            admin = cluster.connect("shared");
            try (Statement s = admin.createStatement()) {
                s.execute("CREATE ROLE app LOGIN");
                s.execute("CREATE TABLE tenants (id int PRIMARY KEY, name text NOT NULL)");
                s.execute(String.format(TABLE, "orders", " tenant_id int NOT NULL REFERENCES tenants,"));
                s.execute("CREATE INDEX orders_tenant_placed ON orders (tenant_id, placed_at DESC)");
                s.execute("ALTER TABLE orders ENABLE ROW LEVEL SECURITY");
                s.execute("ALTER TABLE orders FORCE ROW LEVEL SECURITY");
                s.execute("CREATE POLICY tenant_isolation ON orders USING (tenant_id = " + POLICY + ")");
                s.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON orders TO app");
                s.execute("GRANT SELECT ON tenants TO app");
            }
            app = cluster.connect("shared", "app");
        }

        public String name() {
            return "shared table + RLS";
        }

        public void provision(int tenant) throws SQLException {
            try (PreparedStatement s = admin.prepareStatement("INSERT INTO tenants VALUES (?, ?)")) {
                s.setInt(1, tenant);
                s.setString(2, "chain-" + tenant);
                s.execute();
            }
        }

        public void load(int tenant, int rows) throws SQLException {
            TenantScope.inTenant(admin, tenant, c -> {
                try (Statement s = c.createStatement()) {
                    s.execute(String.format(LOAD, "orders", "tenant_id,", tenant + ",", tenant, rows));
                }
                return null;
            });
        }

        public void migrate(int tenants) throws SQLException {
            try (Statement s = admin.createStatement()) {
                s.execute("ALTER TABLE orders ADD COLUMN currency text NOT NULL DEFAULT 'INR'");
                s.execute("CREATE INDEX orders_tenant_currency ON orders (tenant_id, currency)");
            }
        }

        public long lastOrders(int tenant) throws SQLException {
            return TenantScope.inTenant(app, tenant, c -> {
                try (PreparedStatement s = c.prepareStatement("SELECT total_paise FROM orders WHERE tenant_id = ? ORDER BY placed_at DESC LIMIT 20")) {
                    s.setInt(1, tenant);
                    return first(s);
                }
            });
        }

        public long revenue(int tenant) throws SQLException {
            return TenantScope.inTenant(app, tenant, c -> {
                try (PreparedStatement s = c.prepareStatement("SELECT sum(total_paise) FROM orders WHERE tenant_id = ? AND placed_at > timestamptz '2026-03-01' - interval '30 days'")) {
                    s.setInt(1, tenant);
                    return first(s);
                }
            });
        }

        public int connections() {
            return 1;
        }

        public Connection app() {
            return app;
        }
    }

    /** A schema per tenant with the same tables; requests set search_path for their transaction. */
    final class SchemaPerTenant implements TenancyModel {
        private final Connection c;

        public SchemaPerTenant(Cluster cluster) throws SQLException {
            try (Connection a = cluster.connect("postgres"); Statement s = a.createStatement()) {
                s.execute("CREATE DATABASE schemas");
            }
            c = cluster.connect("schemas");
        }

        public String name() {
            return "schema per tenant";
        }

        public void provision(int tenant) throws SQLException {
            try (Statement s = c.createStatement()) {
                s.execute("CREATE SCHEMA t" + tenant);
                s.execute(String.format(TABLE, "t" + tenant + ".orders", ""));
                s.execute("CREATE INDEX ON t" + tenant + ".orders (placed_at DESC)");
            }
        }

        public void load(int tenant, int rows) throws SQLException {
            try (Statement s = c.createStatement()) {
                s.execute(String.format(LOAD, "t" + tenant + ".orders", "", "", tenant, rows));
            }
        }

        public void migrate(int tenants) throws SQLException {
            try (Statement s = c.createStatement()) {
                for (int t = 1; t <= tenants; t++) {
                    s.execute("ALTER TABLE t" + t + ".orders ADD COLUMN currency text NOT NULL DEFAULT 'INR'");
                    s.execute("CREATE INDEX ON t" + t + ".orders (currency)");
                }
            }
        }

        private <T> T inSchema(int tenant, TenantScope.Work<T> work) throws SQLException {
            c.setAutoCommit(false);
            try {
                try (Statement s = c.createStatement()) {
                    s.execute("SET LOCAL search_path TO t" + tenant);
                }
                T r = work.run(c);
                c.commit();
                return r;
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }

        public long lastOrders(int tenant) throws SQLException {
            return inSchema(tenant, x -> {
                try (PreparedStatement s = x.prepareStatement("SELECT total_paise FROM orders ORDER BY placed_at DESC LIMIT 20")) {
                    return first(s);
                }
            });
        }

        public long revenue(int tenant) throws SQLException {
            return inSchema(tenant, x -> {
                try (PreparedStatement s = x.prepareStatement("SELECT sum(total_paise) FROM orders WHERE placed_at > timestamptz '2026-03-01' - interval '30 days'")) {
                    return first(s);
                }
            });
        }

        public int connections() {
            return 1;
        }
    }

    /** A database per tenant; the application keeps a connection to each. */
    final class DatabasePerTenant implements TenancyModel {
        private final Cluster cluster;
        private final Connection admin;
        private final Map<Integer, Connection> byTenant = new HashMap<>();

        public DatabasePerTenant(Cluster cluster) throws SQLException {
            this.cluster = cluster;
            admin = cluster.connect("postgres");
        }

        public String name() {
            return "database per tenant";
        }

        public Connection connection(int tenant) throws SQLException {
            Connection c = byTenant.get(tenant);
            if (c == null) {
                c = cluster.connect("t" + tenant);
                byTenant.put(tenant, c);
            }
            return c;
        }

        public void provision(int tenant) throws SQLException {
            try (Statement s = admin.createStatement()) {
                s.execute("CREATE DATABASE t" + tenant);
            }
            try (Statement s = connection(tenant).createStatement()) {
                s.execute(String.format(TABLE, "orders", ""));
                s.execute("CREATE INDEX ON orders (placed_at DESC)");
            }
        }

        public void load(int tenant, int rows) throws SQLException {
            try (Statement s = connection(tenant).createStatement()) {
                s.execute(String.format(LOAD, "orders", "", "", tenant, rows));
            }
        }

        public void migrate(int tenants) throws SQLException {
            for (int t = 1; t <= tenants; t++) {
                try (Statement s = connection(t).createStatement()) {
                    s.execute("ALTER TABLE orders ADD COLUMN currency text NOT NULL DEFAULT 'INR'");
                    s.execute("CREATE INDEX ON orders (currency)");
                }
            }
        }

        public long lastOrders(int tenant) throws SQLException {
            try (PreparedStatement s = connection(tenant).prepareStatement("SELECT total_paise FROM orders ORDER BY placed_at DESC LIMIT 20")) {
                return first(s);
            }
        }

        public long revenue(int tenant) throws SQLException {
            try (PreparedStatement s = connection(tenant).prepareStatement("SELECT sum(total_paise) FROM orders WHERE placed_at > timestamptz '2026-03-01' - interval '30 days'")) {
                return first(s);
            }
        }

        public int connections() {
            return byTenant.size();
        }

        public void closeAll() throws SQLException {
            for (Connection c : byTenant.values()) {
                c.close();
            }
            byTenant.clear();
        }
    }
}
