package dev.foodfleet.ch40.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

/** The JDBC calls in order (a recording connection); verify/run.sh runs the same SQL against PostgreSQL 16. */
class TenantTransactionsTest {

    final List<String> log = new ArrayList<>();

    DataSource dataSource() {
        PreparedStatement ps = (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {PreparedStatement.class}, (p, m, a) -> {
                    switch (m.getName()) {
                        case "setString" -> log.add("bind " + a[0] + "=" + a[1]);
                        case "execute" -> {
                            log.add("execute");
                            return true;
                        }
                        default -> {
                        }
                    }
                    return null;
                });
        boolean[] autoCommit = {true};
        Connection c = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Connection.class},
                (p, m, a) -> switch (m.getName()) {
                    case "getAutoCommit" -> autoCommit[0];
                    case "setAutoCommit" -> {
                        autoCommit[0] = (Boolean) a[0];
                        log.add("autocommit " + a[0]);
                        yield null;
                    }
                    case "prepareStatement" -> {
                        log.add("prepare " + a[0]);
                        yield ps;
                    }
                    case "commit", "rollback", "close" -> {
                        log.add(m.getName());
                        yield null;
                    }
                    default -> null;
                });
        return (DataSource) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {DataSource.class},
                (p, m, a) -> m.getName().equals("getConnection") ? c : null);
    }

    @Test
    void setsTheTenantTransactionLocallyBeforeTheWorkAndCommits() throws Exception {
        String r = new TenantTransactions(dataSource()).inTenant(new TenantId("spice-route"), c -> "done");
        assertThat(r).isEqualTo("done");
        assertThat(log).containsExactly("autocommit false", "prepare SELECT set_config('app.tenant_id', ?, true)",
                "bind 1=spice-route", "execute", "commit", "autocommit true", "close");
    }

    @Test
    void rollsBackWhenTheWorkFails() {
        assertThatThrownBy(() -> new TenantTransactions(dataSource()).inTenant(new TenantId("spice-route"), c -> {
            throw new SQLException("boom");
        })).hasMessageContaining("boom");
        assertThat(log).contains("rollback", "autocommit true", "close");
        assertThat(log).doesNotContain("commit");
    }
}
