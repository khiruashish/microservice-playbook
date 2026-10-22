package dev.foodfleet.ch75.tenancy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Runs work for one tenant on a pooled connection, safely: in a transaction whose tenant setting is
 * transaction-local ({@code set_config(..., true)}, the same as {@code SET LOCAL}), so it is gone at commit or
 * rollback and can't leak into the next request that borrows the connection. Row-level security policies read it with
 * {@link TenancyModel#POLICY}, which is NULL when unset, so a request that forgot its tenant sees nothing instead of
 * someone else's rows.
 */
public final class TenantScope {

    @FunctionalInterface
    public interface Work<T> {
        T run(Connection c) throws SQLException;
    }

    private TenantScope() {
    }

    public static <T> T inTenant(Connection c, int tenant, Work<T> work) throws SQLException {
        boolean auto = c.getAutoCommit();
        c.setAutoCommit(false);
        try {
            try (PreparedStatement s = c.prepareStatement("SELECT set_config('app.tenant_id', ?, true)")) {
                s.setString(1, Integer.toString(tenant));
                s.execute();
            }
            T result = work.run(c);
            c.commit();
            return result;
        } catch (SQLException | RuntimeException e) {
            c.rollback();
            throw e;
        } finally {
            c.setAutoCommit(auto);
        }
    }
}
