package dev.foodfleet.lab18;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Isolation tested as the application's own role. Deliberately no @ServiceConnection: it would connect as the
 * container's superuser, which bypasses row-level security, and every test here would pass for the wrong reason.
 */
@SpringBootTest
@Testcontainers
class TenantIsolationTest {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16")
            .withInitScripts("db/roles.sql", "db/test-passwords.sql");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "orders_app");
        registry.add("spring.datasource.password", () -> "orders_app");
        registry.add("spring.flyway.user", () -> "orders_owner_login");
        registry.add("spring.flyway.password", () -> "orders_owner");
    }

    @MockitoBean
    JwtDecoder jwtDecoder;                    // no identity provider needed: these tests call the services directly

    @Autowired
    TenantTransactions tenants;

    @Autowired
    Orders orders;

    @Autowired
    TransactionTemplate transactions;

    @Test
    void eachTenantSeesOnlyItsOwnOrders() {
        tenants.inTenant("spice-route", () -> orders.place("spice-route", "c-1", 45_000));
        tenants.inTenant("spice-route", () -> orders.place("spice-route", "c-2", 30_000));
        tenants.inTenant("dosa-corner", () -> orders.place("dosa-corner", "c-3", 12_000));

        assertThat(tenants.inTenant("spice-route", () -> orders.recent("spice-route", 20)))
                .hasSize(2).allMatch(o -> o.tenantId().equals("spice-route"));
        assertThat(tenants.inTenant("spice-route", orders::countAllVisible)).isEqualTo(2);    // no WHERE: still 2
        assertThat(tenants.inTenant("spice-route", () -> orders.recent("dosa-corner", 20))).isEmpty();
    }

    @Test
    void withoutATenantNothingIsVisible() {
        tenants.inTenant("biryani-house", () -> orders.place("biryani-house", "c-4", 9_000));

        assertThat(transactions.execute(status -> orders.countAllVisible())).isZero();
    }

    @Test
    void aTenantCannotWriteAnotherTenantsRow() {
        assertThatThrownBy(() -> tenants.inTenant("spice-route", () -> orders.place("dosa-corner", "c-5", 1)))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("row-level security");
    }
}
