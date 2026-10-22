package dev.foodfleet.ch61;

import dev.foodfleet.ch61.order.JdbcOrderRepository;
import org.junit.jupiter.api.Tag;

/** The repository tests on PostgreSQL 16, the database production runs (Testcontainers in a real build, chapter 62). */
@Tag("postgres")
class PostgresRepositoryTest extends RepositoryContract {

    @Override
    JdbcOrderRepository.Connections database() throws Exception {
        return TestDatabases.postgres();
    }
}
