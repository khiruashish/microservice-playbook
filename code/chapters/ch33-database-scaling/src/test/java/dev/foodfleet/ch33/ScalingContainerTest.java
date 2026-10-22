package dev.foodfleet.ch33;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.foodfleet.ch33.address.AddressPhase;
import dev.foodfleet.ch33.address.CustomerAddresses;
import dev.foodfleet.ch33.address.KeysetBackfill;
import dev.foodfleet.ch33.schema.InvalidIndexCheck;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Flyway V1-V5 (including the concurrent index build) and the rename steps against real PostgreSQL. Skipped without Docker. */
@SpringBootTest(properties = "app.address-migration.phase=EXPAND")
@Testcontainers(disabledWithoutDocker = true)
class ScalingContainerTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired JdbcClient jdbc;
    @Autowired CustomerAddresses expand;
    @Autowired InvalidIndexCheck indexes;

    @BeforeEach
    void clean() {
        jdbc.sql("TRUNCATE customers, orders").update();
    }

    @Test
    void theMigrationsLeaveAValidIndexAndAValidatedConstraint() {
        assertThat(jdbc.sql("SELECT indisvalid FROM pg_index WHERE indexrelid = 'orders_restaurant_created'::regclass")
                .query(Boolean.class).single()).isTrue();
        assertThat(jdbc.sql("SELECT convalidated FROM pg_constraint WHERE conname = 'orders_amount_positive'")
                .query(Boolean.class).single()).isTrue();
        assertThat(indexes.invalidIndexes()).isEmpty();
        assertThatThrownBy(() -> jdbc.sql("INSERT INTO orders (id, customer_id, restaurant_id, amount_paise, status)"
                + " VALUES (1, 1, 1, 0, 'NEW')").update()).isInstanceOf(DataAccessException.class);
    }

    @Test
    void aRowWrittenByAnOldInstanceAfterTheExpandIsRepairedByTheBackfill() throws InterruptedException {
        expand.register(1, "Asha", "12 MG Road");                                       // a new instance: both columns
        jdbc.sql("INSERT INTO customers (id, name, address) VALUES (2, 'Ravi', '4 Park St')").update();  // an old one
        expand.change(1, "7 Brigade Rd");
        jdbc.sql("UPDATE customers SET address = '9 Church St' WHERE id = 1").update(); // an old instance, later

        var report = new KeysetBackfill(jdbc, 1, Duration.ZERO).run();

        assertThat(report.rowsCopied()).isEqualTo(2L);       // row 2 was empty, row 1 was stale
        var readNew = new CustomerAddresses(jdbc, AddressPhase.READ_NEW);
        assertThat(readNew.find(1)).isEqualTo("9 Church St");
        assertThat(readNew.find(2)).isEqualTo("4 Park St");
        assertThat(new KeysetBackfill(jdbc, 1, Duration.ZERO).run().rowsCopied()).isEqualTo(0L);    // safe to re-run
    }

    @Test
    void afterTheContractStepNewCustomersNoLongerNeedTheOldColumn() {
        var contract = new CustomerAddresses(jdbc, AddressPhase.CONTRACT);
        contract.register(3, "Meera", "1 Lake Rd");
        assertThat(contract.find(3)).isEqualTo("1 Lake Rd");
        assertThat(jdbc.sql("SELECT address IS NULL FROM customers WHERE id = 3").query(Boolean.class).single()).isTrue();
    }
}
