package dev.foodfleet.ch61;

import dev.foodfleet.ch61.order.JdbcOrderRepository;
import org.junit.jupiter.api.Tag;

/** The repository tests on H2 in PostgreSQL mode: fast, and not PostgreSQL. */
@Tag("h2")
class H2RepositoryTest extends RepositoryContract {

    @Override
    JdbcOrderRepository.Connections database() throws Exception {
        return TestDatabases.h2();
    }
}
