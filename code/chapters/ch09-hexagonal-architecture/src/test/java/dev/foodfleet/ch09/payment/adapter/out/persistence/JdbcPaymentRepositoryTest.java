package dev.foodfleet.ch09.payment.adapter.out.persistence;

import dev.foodfleet.ch09.payment.application.port.out.PaymentRepository;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The real adapter against a real (embedded) database, with the production schema. No Spring
 * context needed: an adapter is just a class with dependencies. For PostgreSQL itself, swap in a
 * Testcontainers DataSource (chapter 62); the contract stays the same.
 */
class JdbcPaymentRepositoryTest extends PaymentRepositoryContract {

    private EmbeddedDatabase database;

    @Override
    protected PaymentRepository createRepository() {
        database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("payments-" + UUID.randomUUID())
                .addScript("schema.sql")
                .build();
        return new JdbcPaymentRepository(JdbcClient.create(database),
                new TransactionTemplate(new DataSourceTransactionManager(database)));
    }

    @AfterEach
    void shutdown() {
        database.shutdown();
    }
}
