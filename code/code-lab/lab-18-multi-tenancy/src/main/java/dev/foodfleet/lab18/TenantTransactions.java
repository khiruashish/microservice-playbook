package dev.foodfleet.lab18;

import java.util.function.Supplier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The one way order-service touches the orders table: in a transaction whose first statement names the tenant.
 * {@code set_config(..., true)} is transaction-local (SET LOCAL), so the setting ends at commit or rollback and the
 * next request that borrows the pooled connection starts with no tenant, and sees no rows, rather than this one's.
 * verify/rls.sh: with a session-level SET, a request that forgot its tenant saw 1,000 rows of the previous tenant's.
 */
@Component
public class TenantTransactions {

    private final TransactionTemplate transactions;
    private final JdbcClient jdbc;

    public TenantTransactions(TransactionTemplate transactions, JdbcClient jdbc) {
        this.transactions = transactions;
        this.jdbc = jdbc;
    }

    public <T> T inTenant(String tenantId, Supplier<T> work) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("no tenant");
        }
        return transactions.execute(status -> {
            jdbc.sql("SELECT set_config('app.tenant_id', :tenant, true)").param("tenant", tenantId).query().singleValue();
            return work.get();
        });
    }
}
