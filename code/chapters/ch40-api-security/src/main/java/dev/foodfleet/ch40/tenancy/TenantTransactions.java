package dev.foodfleet.ch40.tenancy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import javax.sql.DataSource;

/**
 * Runs work in a transaction that PostgreSQL row-level security scopes to one tenant
 * ({@code tenancy/schema.sql}). The tenant goes into the transaction-local setting {@code app.tenant_id} with
 * {@code set_config(..., true)}, the equivalent of {@code SET LOCAL}: it ends with the transaction, so a pooled
 * connection never carries one tenant's id into the next borrower's work. A session-level {@code SET} would.
 * <p>
 * The setting only selects rows; it grants nothing. Any code that can run SQL on this connection can change it,
 * so the application role must not be able to reach other tenants by any other route either (no BYPASSRLS, not
 * the table owner, policies forced).
 */
public final class TenantTransactions {

    @FunctionalInterface
    public interface Work<T> {
        T run(Connection connection) throws SQLException;
    }

    private final DataSource dataSource;

    public TenantTransactions(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public <T> T inTenant(TenantId tenant, Work<T> work) throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            boolean autoCommit = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement("SELECT set_config('app.tenant_id', ?, true)")) {
                    ps.setString(1, tenant.value());
                    ps.execute();
                }
                T result = work.run(c);
                c.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(autoCommit);
            }
        }
    }
}
